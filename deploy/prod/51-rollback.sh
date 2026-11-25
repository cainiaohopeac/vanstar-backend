#!/bin/bash
# ============================================================
# 步骤 9: 回滚(本机入口)
#   复用构建机上「已构建过」的 commit 镜像: 不打码不编译, 直接把该版本镜像重新分发到应用机/本机容器。
#   前提: 该 commit 曾被 50-ci-deploy.sh 构建过(镜像会以 <前缀>:<短sha> 保存在构建机)。
# 用法:
#   bash 51-rollback.sh backend  f4c3275        # 后端回滚到 f4c3275
#   bash 51-rollback.sh frontend 7a64ada        # 前端回滚
#   bash 51-rollback.sh list                    # 查看可回滚的镜像版本
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

TARGET=${1:-}
COMMIT=${2:-}
CI_LOCAL=$PROD_DIR/ci
CI_DIR=/data/deploy/ci

sync_scripts() {
  rssh "$BUILD_HOST" "mkdir -p $CI_DIR /data/deploy/logs"
  for f in ci.env.sh build-backend.sh build-frontend.sh; do
    [ -f "$CI_LOCAL/$f" ] || continue
    REMOTE_HASH=$(rssh "$BUILD_HOST" "md5sum $CI_DIR/$f 2>/dev/null | awk '{print \$1}'" 2>/dev/null || echo "")
    LOCAL_HASH=$(md5sum "$CI_LOCAL/$f" | awk '{print $1}')
    [ "$REMOTE_HASH" = "$LOCAL_HASH" ] || rcp "$CI_LOCAL/$f" "$BUILD_HOST" "$CI_DIR"
  done
}

case "$TARGET" in
  list)
    c_info "构建机上可用于回滚的镜像:"
    rssh "$BUILD_HOST" "docker images --format '  {{.Repository}}:{{.Tag}}  创建于 {{.CreatedSince}}' | grep -E 'bzpt-server|bzpt-web'"
    exit 0;;
  backend)
    [ -n "$COMMIT" ] || die "用法: bash 51-rollback.sh backend <短 commit>"
    sync_scripts
    c_info "回滚后端到 ${COMMIT}(不重新编译, 使用构建机已有镜像)"
    rssh "$BUILD_HOST" \
      "APP_HOSTS='$APP_HOSTS' SERVER_IMG='$SERVER_IMG' SERVER_PORTS='$SERVER_PORTS' SSH_USER='$SSH_USER' REMOTE_DIR='$REMOTE_DIR' JAVA_OPTS='$JAVA_OPTS' FORCE_CONF='0' ROLLBACK='1' bash $CI_DIR/build-backend.sh master $COMMIT" < /dev/null
    ;;
  frontend)
    [ -n "$COMMIT" ] || die "用法: bash 51-rollback.sh frontend <短 commit>"
    sync_scripts
    if [ ! -f "$CI_LOCAL/build-frontend.sh" ]; then die "前端链路尚未实现"; fi
    c_info "回滚前端到 ${COMMIT}(不重新编译, 使用构建机已有镜像)"
    rssh "$BUILD_HOST" \
      "WEB_IMG='$WEB_IMG' WEB_PORT='${WEB_PORT:-8089}' GW_PORT='${GW_PORT:-8088}' REMOTE_DIR='$REMOTE_DIR' ROLLBACK='1' bash $CI_DIR/build-frontend.sh master $COMMIT" < /dev/null
    ;;
  *) die "用法: bash 51-rollback.sh {list|backend|frontend} [commit]";;
esac
c_ok "回滚结束: ${TARGET}${COMMIT:+ @ ${COMMIT}}"
