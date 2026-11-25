#!/bin/bash
# ============================================================
# 步骤 8: 远端 CI 发布触发器(本机入口)
#   本机只负责: 同步 ci 脚本到构建机 -> 触发构建 -> 回显日志
#   代码拉取 / Maven / Node 构建 / docker build / 分发 / 滚动发布全部在 171 上用容器完成,
#   本机不需要 JDK / Maven / Node / docker, 也不需要任何基础镜像。
# 用法(在 deploy/prod 目录下, Windows 请用 Git Bash):
#   bash 50-ci-deploy.sh backend  master            # 后端: 拉 master 最新
#   bash 50-ci-deploy.sh backend  master  abc1234   # 后端: 指定 commit
#   bash 50-ci-deploy.sh frontend feature-1030      # 前端
#   bash 50-ci-deploy.sh all      master
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

TARGET=${1:-}
BRANCH=${2:-master}
COMMIT=${3:-}
CI_LOCAL=$PROD_DIR/ci
CI_DIR=/data/deploy/ci

case "$TARGET" in
  backend|frontend|all) ;;
  *) grep -E '^#   bash 50-ci-deploy' "$0" | sed 's/^#   //'; die "示例用法见上";;
esac

c_info "发布目标=${TARGET} 分支=${BRANCH}${COMMIT:+ commit=${COMMIT}}"

# ---------- 1. 同步 ci 脚本到构建机(内容变化才覆盖) ----------
c_info "[1/3] 同步 CI 脚本到构建机 $BUILD_HOST"
rssh "$BUILD_HOST" "mkdir -p $CI_DIR /data/deploy/logs"
for f in ci.env.sh build-backend.sh build-frontend.sh; do
  [ -f "$CI_LOCAL/$f" ] || continue
  REMOTE_HASH=$(rssh "$BUILD_HOST" "md5sum $CI_DIR/$f 2>/dev/null | awk '{print \$1}'" 2>/dev/null || echo "")
  LOCAL_HASH=$(md5sum "$CI_LOCAL/$f" | awk '{print $1}')
  if [ "$REMOTE_HASH" = "$LOCAL_HASH" ]; then
    echo "  未变化: $f"
  else
    rcp "$CI_LOCAL/$f" "$BUILD_HOST" "$CI_DIR" && echo "  已更新: $f"
  fi
done

run_backend() {
  [ -f "$CI_LOCAL/build-backend.sh" ] || die "缺少 $CI_LOCAL/build-backend.sh"
  c_info "[2/3] 触发后端: 171 拉码 -> Maven 容器打包 -> 构建 -> 分发 ${APP_HOSTS}"
  # 注意: 脚本先上传再执行(不能通过 stdin 喂给远端 bash), 否则脚本内部的 ssh 会把 stdin 上的剩余脚本吞掉
  rssh "$BUILD_HOST" \
    "APP_HOSTS='$APP_HOSTS' SERVER_IMG='$SERVER_IMG' SERVER_PORTS='$SERVER_PORTS' SSH_USER='$SSH_USER' REMOTE_DIR='$REMOTE_DIR' JAVA_OPTS='$JAVA_OPTS' FORCE_CONF='${FORCE_CONF:-0}' bash $CI_DIR/build-backend.sh $BRANCH ${COMMIT:-}" < /dev/null
}

run_frontend() {
  if [ ! -f "$CI_LOCAL/build-frontend.sh" ]; then
    c_warn "前端链路尚未实现(第二步), 跳过"
    return 0
  fi
  c_info "[2/3] 触发前端: 171 拉码 -> Node 容器构建 -> 构建镜像 -> 重启容器"
  rssh "$BUILD_HOST" \
    "WEB_IMG='$WEB_IMG' WEB_PORT='${WEB_PORT:-8089}' GW_PORT='${GW_PORT:-8088}' SSH_USER='$SSH_USER' REMOTE_DIR='$REMOTE_DIR' PROD_API_TARGET='${PROD_API_TARGET:-}' UPLOADS_TARGET='${UPLOADS_TARGET:-}' bash $CI_DIR/build-frontend.sh $BRANCH ${COMMIT:-}" < /dev/null
}

case "$TARGET" in
  backend)  run_backend;;
  frontend) run_frontend;;
  all)      run_backend; run_frontend;;
esac

c_ok "发布流程结束: ${TARGET} ${BRANCH}${COMMIT:+ @ ${COMMIT}}"
