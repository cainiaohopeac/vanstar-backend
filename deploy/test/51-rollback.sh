#!/bin/bash
# ============================================================
# 测试环境回滚(本机入口)
#   复用 187 上「已构建过」的 commit 镜像: 不拉码不编译, 直接把该版本镜像重新跑起来。
#   前提: 该 commit 曾被 50-ci-deploy.sh 构建过(镜像以 <前缀>:<短sha> 保存在 187)。
# 用法:
#   bash 51-rollback.sh list                      # 查看 187 上可回滚的镜像版本
#   bash 51-rollback.sh backend  f4c3275          # 后端回滚到 f4c3275
#   bash 51-rollback.sh frontend 7a64ada          # 前端回滚
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

TARGET=${1:-}
COMMIT=${2:-}
CI_LOCAL=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ci

sync_scripts() {
  tssh "mkdir -p $CI_DIR $LOG_DIR"
  for f in ci.env.sh build-backend.sh build-frontend.sh; do
    [ -f "$CI_LOCAL/$f" ] || continue
    REMOTE_HASH=$(tssh "md5sum $CI_DIR/$f 2>/dev/null | awk '{print \$1}'" 2>/dev/null || echo "")
    LOCAL_HASH=$(md5 -q "$CI_LOCAL/$f" 2>/dev/null || md5sum "$CI_LOCAL/$f" | awk '{print $1}')
    [ "$REMOTE_HASH" = "$LOCAL_HASH" ] || tcp "$CI_LOCAL/$f" "$CI_DIR"
  done
}

case "$TARGET" in
  list)
    c_info "187 上可用于回滚的镜像:"
    tssh "docker images --format '  {{.Repository}}:{{.Tag}}  创建于 {{.CreatedSince}}' | grep -E 'bzpt-server|bzpt-web'"
    ;;
  backend)
    [ -n "$COMMIT" ] || die "用法: bash 51-rollback.sh backend <短 commit>"
    sync_scripts
    c_info "回滚后端到 ${COMMIT}(不重新编译, 使用 187 已有镜像)"
    tssh \
      "SERVER_IMG='$SERVER_IMG' SERVER_PORT='$SERVER_PORT' SNAIL_PORT='$SNAIL_PORT' JAVA_OPTS='$JAVA_OPTS' HOST_CONF_DIR='$HOST_CONF_DIR' HOST_LOG_DIR='$HOST_LOG_DIR' FORCE_CONF='0' ROLLBACK='1' bash $CI_DIR/build-backend.sh master-1030 $COMMIT" < /dev/null
    ;;
  frontend)
    [ -n "$COMMIT" ] || die "用法: bash 51-rollback.sh frontend <短 commit>"
    sync_scripts
    c_info "回滚前端到 ${COMMIT}(不重新编译, 使用 187 已有镜像)"
    tssh \
      "WEB_IMG='$WEB_IMG' WEB_PORT='$WEB_PORT' PROD_API_TARGET='$PROD_API_TARGET' UPLOADS_TARGET='$UPLOADS_TARGET' ROLLBACK='1' bash $CI_DIR/build-frontend.sh master-1030 $COMMIT" < /dev/null
    ;;
  *) die "用法: bash 51-rollback.sh {list|backend|frontend} [commit]";;
esac
c_ok "回滚结束: ${TARGET}${COMMIT:+ @ ${COMMIT}}"
