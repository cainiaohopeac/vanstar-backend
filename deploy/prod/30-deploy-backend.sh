#!/bin/bash
# ============================================================
# 步骤 6: 发布后端服务 -> 192.168.0.32 / 192.168.0.211(每台 8081/8082)
# 流程(本地不依赖 docker):
#   1) 本地 mvn 产出 jar, 与 Dockerfile 一起打成构建上下文包
#   2) 上传上下文包到构建机 192.168.0.171
#   3) 在 171 上 docker build(使用 11-prepare-base-images.sh 载入的 JDK17 基础镜像)
#   4) 在 171 上 docker save|gzip, 经内网分发到各应用机
#   5) 各应用机 docker load -> 外置配置初始化 -> 滚动重启 -> 健康检查
# 用法:
#   bash 30-deploy-backend.sh              # 用已有 jar 发布
#   bash 30-deploy-backend.sh --build      # 先 mvn package 再发布
#   bash 30-deploy-backend.sh --force-conf # 用随包配置覆盖服务器上的外置配置(慎用)
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

BUILD=0; FORCE_CONF=0
for a in "$@"; do
  case "$a" in
    --build) BUILD=1;;
    --force-conf) FORCE_CONF=1;;
  esac
done

JAR=$ROOT/ruoyi-admin/target/ruoyi-admin.jar
TAG=${SERVER_IMG##*:}
CTX_TAR=backend-ctx.tar.gz
IMG_TAR=bzpt-server-${TAG}.tar.gz

# ---------- 1. 本地产出 jar ----------
if [ "$BUILD" = "1" ] || [ ! -f "$JAR" ]; then
  c_info "[1/5] mvn package ..."
  command -v mvn >/dev/null 2>&1 || die "本地缺少 mvn, 请先在 IDE/命令行执行: mvn -pl ruoyi-admin -am package -DskipTests"
  (cd "$ROOT" && mvn -q -pl ruoyi-admin -am package -DskipTests)
else
  c_info "[1/5] 使用已有 jar: $JAR"
fi
[ -f "$JAR" ] || die "jar 不存在: $JAR"

# ---------- 2. 打包构建上下文 ----------
c_info "[2/5] 打包构建上下文 -> $CTX_TAR"
mkdir -p "$STAGE_DIR"
CTX_PATH=$STAGE_DIR/$CTX_TAR
tar -czf "$CTX_PATH" -C "$ROOT/ruoyi-admin" Dockerfile target/ruoyi-admin.jar
ls -lh "$CTX_PATH" | awk '{print "      大小: "$5}'

# ---------- 3. 上传并在 171 上构建 ----------
c_info "[3/5] 上传 $BUILD_HOST 并构建镜像 $SERVER_IMG"
rssh "$BUILD_HOST" "mkdir -p $BUILD_DIR/backend $REMOTE_DIR"
rcp "$CTX_PATH" "$BUILD_HOST" "$REMOTE_DIR"

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/build-remote.sh" <<'EOS'
set -euo pipefail
CTX_TAR=$1
IMG=$2
TAG=$3
JAVA_BASE_DEFAULT=$4
D=/data/deploy
B=$D/build/backend
mkdir -p "$B"
cd "$B"
tar -xzf "$D/$CTX_TAR"
[ -f "$D/base-images.env" ] && . "$D/base-images.env"
JAVA_BASE=${RESOLVED_JAVA_IMG:-$JAVA_BASE_DEFAULT}
echo "  JDK 基础镜像: $JAVA_BASE"
docker build --build-arg JAVA_BASE_IMG="$JAVA_BASE" -t "$IMG" -f Dockerfile .
echo "  docker save -> $D/bzpt-server-$TAG.tar.gz"
docker save "$IMG" | gzip -1 > "$D/bzpt-server-$TAG.tar.gz"
ls -lh "$D/bzpt-server-$TAG.tar.gz" | awk '{print "  镜像包: "$5}'
docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep "$IMG" || true
EOS

rssh "$BUILD_HOST" "bash -s" < "$TMP/build-remote.sh" "$CTX_TAR" "$SERVER_IMG" "$TAG" "$JAVA_BASE_IMG" \
  && c_ok "镜像构建完成(在 $BUILD_HOST 上)" || die "镜像构建失败"

# ---------- 4. 配置模板 ----------
if ls "$PROD_DIR"/conf/application*.yml >/dev/null 2>&1; then
  CONF_SRC="$PROD_DIR/conf"; c_info "[4/5] 使用生产配置模板: $CONF_SRC"
else
  CONF_SRC="$ROOT/ruoyi-admin/target/classes"
  c_warn "[4/5] 未找到 $PROD_DIR/conf/application*.yml, 使用 Maven 过滤产物: $CONF_SRC"
fi

cat > "$TMP/deploy-remote.sh" <<'EOS'
set -euo pipefail
FORCE=${1:-0}
# JAVA_OPTS / TAG / IMG 通过环境变量传入(避免多词 JAVA_OPTS 被 ssh 拆词错位)
IMG=${IMG:?未设置 IMG}
TAG=${TAG:?未设置 TAG}
JAVA_OPTS=${JAVA_OPTS:-}
cd /data/deploy

docker load -i bzpt-server-$TAG.tar.gz
mkdir -p /data/bzpt/config /data/bzpt/logs-8081 /data/bzpt/logs-8082

# 外置配置: 首次用随包模板初始化, 之后以服务器文件为准(改配置只需 restart)
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
  # --add-host: 应用机无公网出口, OSS(oss-cn-beijing) 经接入层 171 的 oss-fwd(nginx stream 443) 转发。
  # 注意必须同时映射带 bucket 前缀的域名: MinIO SDK 是 virtual-host 风格, putObject 连的是 mark-server.oss-cn-beijing...
  docker run -d --name bzpt-server-$port --network host --restart unless-stopped \
    -e TZ=Asia/Shanghai \
    -e SERVER_PORT=$port \
    -e SNAIL_PORT=2$port \
    -e JAVA_OPTS="$JAVA_OPTS" \
    --add-host oss-cn-beijing.aliyuncs.com:192.168.0.171 \
    --add-host mark-server.oss-cn-beijing.aliyuncs.com:192.168.0.171 \
    -v /data/bzpt/config:/ruoyi/server/config \
    -v /data/bzpt/logs-$port:/ruoyi/server/logs \
    "$IMG"

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

# ---------- 5. 由 171 分发到各应用机并滚动发布 ----------
c_info "[5/5] 滚动发布到 $APP_HOSTS"
rssh "$BUILD_HOST" "for h in $APP_HOSTS; do ssh $SSH_OPT $SSH_USER@\$h 'mkdir -p $REMOTE_DIR'; scp $SSH_OPT $REMOTE_DIR/$IMG_TAR $SSH_USER@\$h:$REMOTE_DIR/; done"

for h in $APP_HOSTS; do
  c_info "==== $h ===="
  rssh "$h" "mkdir -p $REMOTE_DIR/conf-seed"
  for f in "$CONF_SRC"/application*.yml; do
    rcp "$f" "$h" "$REMOTE_DIR/conf-seed"
  done
  rssh "$h" "IMG='$SERVER_IMG' TAG='$TAG' JAVA_OPTS='$JAVA_OPTS' bash -s $FORCE_CONF" < "$TMP/deploy-remote.sh" \
    && c_ok "$h 发布完成" || die "$h 发布失败, 已中止(其余机器未变更)"

  # 跨机可达性校验: 容器内 curl 只证明本机通, 还需确认网关能访问(安全组是否放行)
  for port in $SERVER_PORTS; do
    code=$(rssh "$GW_IP" "curl -s -m 8 -o /dev/null -w '%{http_code}' http://$h:$port/auth/tenant/list" 2>/dev/null || echo 000)
    if [ "$code" = "200" ]; then
      c_ok "$h:$port 从网关可达"
    else
      c_warn "$h:$port 从网关访问不通($code) —— 需放行内网段访问该端口, 否则网关转发会挂起(499)"
    fi
  done
done

rssh "$BUILD_HOST" "rm -f $REMOTE_DIR/$IMG_TAR $REMOTE_DIR/$CTX_TAR"
c_ok "后端发布完成"
c_warn "首次部署后请确认 /data/bzpt/config/application-dev.yml 中 MySQL/Redis/MinIO 地址是否为生产地址"
