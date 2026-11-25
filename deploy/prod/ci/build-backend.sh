#!/bin/bash
# ============================================================
# 后端远端构建 + 滚动发布(在构建机 171 上执行, 由本机 50-ci-deploy.sh 触发)
# 流程: 拉代码(容器 git) -> Maven 容器打包 -> docker build(基础镜像取自 base-images.env)
#       -> 分发到应用机 32/211 -> 逐台 load + 滚动重启 + 健康检查
# 入参: $1=分支(默认 master)  $2=commit(可选, 指定则不随分支更新到最新)
# 环境变量(由触发器注入): APP_HOSTS SERVER_IMG SERVER_PORTS SSH_USER REMOTE_DIR JAVA_OPTS FORCE_CONF
# ============================================================
set -euo pipefail
CI_ENV=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd)/ci.env.sh
[ -f "$CI_ENV" ] || CI_ENV=/data/deploy/ci/ci.env.sh
source "$CI_ENV"

BRANCH=${1:-master}
COMMIT=${2:-}
SSH_USER=${SSH_USER:-root}
SERVER_IMG=${SERVER_IMG:?需传入 SERVER_IMG, 例 bzpt-server:5.X}
SERVER_PORTS=${SERVER_PORTS:-8081 8082}
APP_HOSTS=${APP_HOSTS:?需传入 APP_HOSTS}
JAVA_OPTS=${JAVA_OPTS:-}
FORCE_CONF=${FORCE_CONF:-0}
ROLLBACK=${ROLLBACK:-0}
TAG=${SERVER_IMG##*:}
IMG_TAR=bzpt-server-$TAG.tar.gz

mkdir -p "$LOG_DIR" "$BUILD_DIR/backend"
RUN_LOG=$LOG_DIR/backend-$(date +%Y%m%d-%H%M%S).log
exec > >(tee "$RUN_LOG") 2>&1

ci_lock

# 回滚模式: 复用历史构建的 commit 镜像, 不重新拉码/编译
if [ "$ROLLBACK" = "1" ]; then
  [ -n "$COMMIT" ] || die "回滚必须指定 commit: bash 51-rollback.sh backend <commit>"
  SHA=$COMMIT
  if docker image inspect "${SERVER_IMG%:*}:$SHA" >/dev/null 2>&1; then
    docker tag "${SERVER_IMG%:*}:$SHA" "$SERVER_IMG"
    log "===== 后端回滚: $SERVER_IMG -> $SHA (使用已有镜像) ====="
  else
    die "构建机上没有 $SERVER_IMG@$SHA 的历史镜像, 请先确认该 commit 曾被构建过"
  fi
  docker save "$SERVER_IMG" | gzip -1 > "$REMOTE_DIR/$IMG_TAR"
  log "       回滚镜像包: $(ls -lh "$REMOTE_DIR/$IMG_TAR" | awk '{print $5}')"
  # 直接跳到分发/滚动发布
  SOURCE_CONF=$BACKEND_SRC/deploy/prod/conf
  if [ ! -d "$SOURCE_CONF" ]; then
    log "[WARN] 找不到配置模板目录(代码未检出), 跳过配置种子下发, 沿用各机现有 /data/bzpt/config"
  fi
  jump_to=4
else
  jump_to=1
fi

log "===== 后端发布开始: 分支=$BRANCH ${COMMIT:+commit=$COMMIT} ====="

if [ "$jump_to" -le 1 ]; then
# ---------- 1. 拉代码 ----------
log "[1/6] 检出代码 $BACKEND_REPO"
SHA=$(git_sync "$BACKEND_REPO" bzpt-backend "$BRANCH" "$COMMIT" | tail -1 | tr -d '\r\n')
case "$SHA" in
  [0-9a-f]*[0-9a-f]) [ ${#SHA} -le 40 ] || die "无法解析代码版本号: $SHA";;
  *) die "无法解析代码版本号: $SHA";;
esac
log "       代码版本: $SHA"
SOURCE_CONF=$BACKEND_SRC/deploy/prod/conf
[ -d "$SOURCE_CONF" ] || SOURCE_CONF=$BACKEND_SRC/ruoyi-admin/target/classes
log "       配置模板目录: $SOURCE_CONF"

# ---------- 2. Maven 构建 ----------
log "[2/6] Maven 构建(容器 $MAVEN_IMG, 依赖缓存卷 $M2_VOL)"
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
# ruoyi-admin/Dockerfile 的上下文期望 <模块目录>/target/ruoyi-admin.jar
MODULE_DIR=$BACKEND_SRC/ruoyi-admin
EXPECTED=$MODULE_DIR/target/ruoyi-admin.jar
if [ ! -f "$EXPECTED" ]; then
  log "       产物名不是 ruoyi-admin.jar, 复制为期望名称: $(basename "$JAR") -> ruoyi-admin.jar"
  cp "$JAR" "$EXPECTED"
fi

# ---------- 3. 构建镜像 ----------
log "[3/6] 构建镜像 $SERVER_IMG (并打标签 $SHA)"
[ -f "$REMOTE_DIR/base-images.env" ] && . "$REMOTE_DIR/base-images.env"
JAVA_BASE=${RESOLVED_JAVA_IMG:-${JAVA_BASE_DEFAULT:-eclipse-temurin:17-jre-jammy}}
log "       JDK 基础镜像: $JAVA_BASE"
cd "$MODULE_DIR"
docker build --build-arg JAVA_BASE_IMG="$JAVA_BASE" \
  -t "$SERVER_IMG" -t "${SERVER_IMG%:*}:$SHA" . 2>&1 | tail -5
docker save "$SERVER_IMG" | gzip -1 > "$REMOTE_DIR/$IMG_TAR"
log "       镜像包: $(ls -lh "$REMOTE_DIR/$IMG_TAR" | awk '{print $5}')"
fi

# ---------- 4. 分发到应用机 ----------
log "[4/6] 分发镜像包到 $APP_HOSTS"
for h in $APP_HOSTS; do
  # ssh/scp 统一加 < /dev/null 与 -n, 避免它们读走本脚本自身的 stdin(经 stdin 执行时会导致后续步骤被吞)
  ssh -n -o StrictHostKeyChecking=no "$SSH_USER@$h" "mkdir -p $REMOTE_DIR; rm -rf $REMOTE_DIR/conf-seed; mkdir -p $REMOTE_DIR/conf-seed" < /dev/null
  scp -o StrictHostKeyChecking=no "$REMOTE_DIR/$IMG_TAR" "$SSH_USER@$h:$REMOTE_DIR/" >/dev/null < /dev/null
  if [ -d "$SOURCE_CONF" ]; then
    scp -o StrictHostKeyChecking=no "$SOURCE_CONF"/application*.yml "$SSH_USER@$h:$REMOTE_DIR/conf-seed/" >/dev/null < /dev/null
  fi
  log "       $h 已接收"
done

# ---------- 5. 应用机滚动发布 ----------
APP_SCRIPT=$BUILD_DIR/backend/app-deploy.sh
cat > "$APP_SCRIPT" <<'EOS'
set -euo pipefail
IMG=${IMG:?} TAG=${TAG:?} JAVA_OPTS=${JAVA_OPTS:-} FORCE=${FORCE:-0}
cd /data/deploy
docker load -i bzpt-server-$TAG.tar.gz
mkdir -p /data/bzpt/config
for port in 8081 8082; do mkdir -p /data/bzpt/logs-$port; done
# 外置配置: 首次用随包模板初始化, 之后以服务器文件为准
if [ -d conf-seed ]; then
  for f in conf-seed/*.yml; do
    b=$(basename "$f")
    if [ ! -f "/data/bzpt/config/$b" ] || [ "$FORCE" = "1" ]; then
      cp "$f" "/data/bzpt/config/$b"; echo "  初始化配置: $b"
    else
      echo "  保留已有配置: $b"
    fi
  done
fi
for port in 8081 8082; do
  echo "-- 重启实例 $port --"
  docker rm -f bzpt-server-$port >/dev/null 2>&1 || true
  docker run -d --name bzpt-server-$port --network host --restart unless-stopped \
    -e TZ=Asia/Shanghai \
    -e SERVER_PORT=$port -e SNAIL_PORT=2$port -e JAVA_OPTS="$JAVA_OPTS" \
    -v /data/bzpt/config:/ruoyi/server/config \
    -v /data/bzpt/logs-$port:/ruoyi/server/logs \
    "$IMG" >/dev/null
  ok=0
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/auth/tenant/list" || true)
    [ "$code" = "200" ] && { ok=1; break; }
    sleep 3
  done
  if [ "$ok" = "1" ]; then
    echo "  实例 $port 健康检查通过"
  else
    echo "  实例 $port 健康检查失败, 最近日志:"; docker logs --tail 60 bzpt-server-$port; exit 1
  fi
done
EOS

log "[5/6] 滚动发布到 $APP_HOSTS"
for h in $APP_HOSTS; do
  log "==== $h ===="
  # 先把脚本 scp 过去再执行: 不依赖 ssh 的 stdin 透传(更可靠, 且远端报错能直接暴露)
  scp -o StrictHostKeyChecking=no "$APP_SCRIPT" "$SSH_USER@$h:$REMOTE_DIR/app-deploy.sh" >/dev/null < /dev/null
  ssh -n -o StrictHostKeyChecking=no "$SSH_USER@$h" \
    "IMG='$SERVER_IMG' TAG='$TAG' JAVA_OPTS='$JAVA_OPTS' FORCE='$FORCE_CONF' bash $REMOTE_DIR/app-deploy.sh" < /dev/null \
    || die "$h 发布失败, 已中止(其余机器未变更)"
  for port in $SERVER_PORTS; do
    # 跨机可达性校验: 容器内 curl 只证明本机通, 还需确认网关能访问(安全组是否放行)
    code=$(curl -s -m 8 -o /dev/null -w '%{http_code}' "http://$h:$port/auth/tenant/list" || echo 000)
    if [ "$code" = "200" ]; then
      log "       $h:$port 从网关可达"
    else
      log "       [WARN] $h:$port 从网关访问不通($code) —— 需放行内网段访问该端口"
    fi
  done
done

# ---------- 6. 收尾 ----------
rm -f "$REMOTE_DIR/$IMG_TAR"
ls -1t "$LOG_DIR"/backend-*.log 2>/dev/null | tail -n +$((KEEP_LOGS + 1)) | xargs -r rm -f
log "===== 后端发布完成: $SERVER_IMG @ $SHA (日志: $RUN_LOG) ====="
log "提示: 首次部署请确认 /data/bzpt/config/application-dev.yml 中 MySQL/Redis/MinIO 地址为生产地址"
