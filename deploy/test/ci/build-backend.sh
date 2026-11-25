#!/bin/bash
# ============================================================
# 后端远端构建 + 发布(在测试机 187 上执行, 由本机 deploy/test/50-ci-deploy.sh 触发)
# 流程: 拉代码(容器 git) -> Maven 容器打包 -> docker build(JDK 基础镜像用生产的)
#       -> 覆盖重启本机 bzpt-server 容器(8081) + 健康检查
# 入参: $1=分支(默认 master-1030)  $2=commit(可选, 指定则不随分支更新到最新)
# 环境变量(由触发器注入): SERVER_IMG SERVER_PORT SNAIL_PORT JAVA_OPTS HOST_CONF_DIR HOST_LOG_DIR FORCE_CONF
# ============================================================
set -euo pipefail
CI_ENV=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd)/ci.env.sh
[ -f "$CI_ENV" ] || CI_ENV=/root/bzpt-deploy/ci/ci.env.sh
source "$CI_ENV"

BRANCH=${1:-master-1030}
COMMIT=${2:-}
SERVER_IMG=${SERVER_IMG:?需传入 SERVER_IMG, 例 bzpt-server:5.X}
SERVER_PORT=${SERVER_PORT:-8081}
SNAIL_PORT=${SNAIL_PORT:-28080}
JAVA_OPTS=${JAVA_OPTS:-"-Xms256m -Xmx1536m -XX:MaxMetaspaceSize=512m"}
HOST_CONF_DIR=${HOST_CONF_DIR:-/docker/bzpt-server/config}
HOST_LOG_DIR=${HOST_LOG_DIR:-/docker/bzpt-server/logs}
FORCE_CONF=${FORCE_CONF:-0}
ROLLBACK=${ROLLBACK:-0}
TAG=${SERVER_IMG##*:}
IMG_TAR=bzpt-server-$TAG.tar.gz

mkdir -p "$LOG_DIR" "$BUILD_DIR/backend"
RUN_LOG=$LOG_DIR/backend-$(date +%Y%m%d-%H%M%S).log
exec > >(tee "$RUN_LOG") 2>&1

ci_lock

log "===== [测试] 后端发布开始: 分支=$BRANCH ${COMMIT:+commit=$COMMIT} ====="

if [ "$ROLLBACK" = "1" ]; then
  [ -n "$COMMIT" ] || die "回滚必须指定 commit: bash 51-rollback.sh backend <短commit>"
  SHA=$COMMIT
  if docker image inspect "${SERVER_IMG%:*}:$SHA" >/dev/null 2>&1; then
    docker tag "${SERVER_IMG%:*}:$SHA" "$SERVER_IMG"
    log "回滚模式: $SERVER_IMG -> $SHA(复用已有镜像, 跳过拉码/编译)"
  else
    die "187 上没有 $SERVER_IMG@$SHA 的历史镜像, 请确认该 commit 曾被构建过"
  fi
  SOURCE_CONF=""
  jump_to=3
else
  jump_to=1
fi

if [ "$jump_to" -le 1 ]; then
# ---------- 1. 拉代码 ----------
log "[1/5] 检出代码 $BACKEND_REPO"
SHA=$(git_sync "$BACKEND_REPO" bzpt-backend "$BRANCH" "$COMMIT" | tail -1 | tr -d '\r\n')
case "$SHA" in
  [0-9a-f]*[0-9a-f]) [ ${#SHA} -le 40 ] || die "无法解析代码版本号: $SHA";;
  *) die "无法解析代码版本号: $SHA";;
esac
log "       代码版本: $SHA"
SOURCE_CONF=$BACKEND_SRC/ruoyi-admin/target/classes

# ---------- 2. Maven 构建 ----------
log "[2/5] Maven 构建(容器 $MAVEN_IMG, 依赖缓存卷 $M2_VOL)"
MVN_LOG=$BUILD_DIR/backend/mvn.log
if docker run --rm -v "$BACKEND_SRC:/src" -v "$M2_VOL:/root/.m2" -w /src \
     -e MAVEN_OPTS="-Dfile.encoding=UTF-8" \
     "$MAVEN_IMG" mvn -B -pl ruoyi-admin -am package -DskipTests > "$MVN_LOG" 2>&1; then
  log "       mvn 构建成功"
else
  tail -40 "$MVN_LOG"; die "Maven 构建失败(完整日志: $MVN_LOG)"
fi
JAR=$(ls "$BACKEND_SRC"/ruoyi-admin/target/*.jar 2>/dev/null | head -1)
[ -n "$JAR" ] || die "未找到构建产物 jar"
EXPECTED=$BACKEND_SRC/ruoyi-admin/target/ruoyi-admin.jar
if [ ! -f "$EXPECTED" ]; then
  log "       产物名不是 ruoyi-admin.jar, 复制为期望名称: $(basename "$JAR") -> ruoyi-admin.jar"
  cp "$JAR" "$EXPECTED"
fi

# ---------- 3. 构建镜像(基础镜像用生产的, 见 base-images.env) ----------
log "[3/5] 构建镜像 $SERVER_IMG (并打标签 $SHA)"
[ -f "$REMOTE_DIR/base-images.env" ] && . "$REMOTE_DIR/base-images.env"
JAVA_BASE=${RESOLVED_JAVA_IMG:-${JAVA_BASE_DEFAULT:-bzpt-java17:17.0.11-cds}}
log "       JDK 基础镜像: $JAVA_BASE"
cd "$BACKEND_SRC/ruoyi-admin"
docker build --build-arg JAVA_BASE_IMG="$JAVA_BASE" \
  -t "$SERVER_IMG" -t "${SERVER_IMG%:*}:$SHA" . 2>&1 | tail -5
fi

# ---------- 4. 外置配置种子(仅首次/FORCE_CONF 时覆盖) ----------
log "[4/5] 外置配置: $HOST_CONF_DIR (首次用随码模板初始化, 之后以服务器文件为准)"
mkdir -p "$HOST_CONF_DIR" "$HOST_LOG_DIR"
if [ "$jump_to" -le 1 ] && [ -d "$SOURCE_CONF" ]; then
  CONF_SEED=$BUILD_DIR/backend/conf-seed
  rm -rf "$CONF_SEED"; mkdir -p "$CONF_SEED"
  cp "$SOURCE_CONF"/application*.yml "$CONF_SEED/" 2>/dev/null || true
  for f in "$CONF_SEED"/*.yml; do
    [ -e "$f" ] || continue
    b=$(basename "$f")
    if [ ! -f "$HOST_CONF_DIR/$b" ] || [ "$FORCE_CONF" = "1" ]; then
      cp "$f" "$HOST_CONF_DIR/$b"; log "       写入配置: $b"
    else
      log "       保留已有配置: $b"
    fi
  done
fi

# ---------- 5. 覆盖重启容器 + 健康检查 ----------
log "[5/5] 覆盖重启 bzpt-server($SERVER_PORT)..."
docker rm -f bzpt-server >/dev/null 2>&1 || true
docker run -d --name bzpt-server --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e SERVER_PORT=$SERVER_PORT \
  -e SNAIL_PORT=$SNAIL_PORT \
  -e JAVA_OPTS="$JAVA_OPTS" \
  -v "$HOST_CONF_DIR":/ruoyi/server/config \
  -v "$HOST_LOG_DIR":/ruoyi/server/logs \
  "$SERVER_IMG" >/dev/null

ok=0
for i in $(seq 1 30); do
  code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$SERVER_PORT/auth/tenant/list" || true)
  [ "$code" = "200" ] && { ok=1; break; }
  sleep 5
done
if [ "$ok" = "1" ]; then
  log "       健康检查通过: http://127.0.0.1:$SERVER_PORT/auth/tenant/list -> 200"
else
  log "       健康检查失败, 最近日志:"; docker logs --tail 60 bzpt-server || true
  die "后端启动异常, 检查 $HOST_CONF_DIR 配置或 docker logs bzpt-server"
fi
docker ps --format '{{.Names}}|{{.Status}}' | grep bzpt-server || true

rm -f "$REMOTE_DIR/$IMG_TAR" 2>/dev/null || true
ls -1t "$LOG_DIR"/backend-*.log 2>/dev/null | tail -n +$((KEEP_LOGS + 1)) | xargs -r rm -f
log "===== [测试] 后端发布完成: $SERVER_IMG @ $SHA (日志: $RUN_LOG) ====="
