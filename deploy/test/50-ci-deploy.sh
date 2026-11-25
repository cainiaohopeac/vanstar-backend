#!/bin/bash
# ============================================================
# 测试环境远端 CI 发布触发器(本机入口, 唯一日常命令)
#   本机只负责: 同步 ci 脚本到 187(比对 md5) -> 触发构建 -> 实时回显日志
#   拉码 / Maven / Node 构建 / docker build / 重启容器 / 健康检查全部在 187 上用容器完成,
#   本机不需要 JDK / Maven / Node / docker, 也不需要任何基础镜像。
#   依赖镜像(JDK17 / nginx / maven / node / git)复用生产环境的,
#   由 10-prepare-base-images.sh 一次性从生产 171 迁移到 187。
# 用法(在 deploy/test 目录下):
#   bash 50-ci-deploy.sh backend  master-1030         # 后端: 拉 master-1030 最新
#   bash 50-ci-deploy.sh backend  master-1030 abc1234 # 后端: 指定 commit
#   bash 50-ci-deploy.sh frontend feature-1030        # 前端
#   bash 50-ci-deploy.sh all      master-1030         # 前后端一起
# FORCE_CONF=1 bash 50-ci-deploy.sh backend master-1030   # 用随码配置覆盖 187 外置配置(慎用)
# 未配免密时: BZPT_SSH_PASS='密码' bash 50-ci-deploy.sh ...
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

TARGET=${1:-}
BRANCH=${2:-master-1030}
COMMIT=${3:-}
CI_LOCAL=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ci

case "$TARGET" in
  backend|frontend|all) ;;
  *) grep -E '^#   bash 50-ci-deploy' "$0" | sed 's/^#   //'; die "示例用法见上";;
esac

c_info "发布目标=${TARGET} 分支=${BRANCH}${COMMIT:+ commit=${COMMIT}} 测试机=$TEST_HOST"

# ---------- 1. 同步 ci 脚本到 187(内容变化才覆盖) ----------
c_info "[1/3] 同步 CI 脚本到 $TEST_HOST:$CI_DIR"
tssh "mkdir -p $CI_DIR $LOG_DIR"
for f in ci.env.sh build-backend.sh build-frontend.sh; do
  [ -f "$CI_LOCAL/$f" ] || continue
  REMOTE_HASH=$(tssh "md5sum $CI_DIR/$f 2>/dev/null | awk '{print \$1}'" 2>/dev/null || echo "")
  LOCAL_HASH=$(md5 -q "$CI_LOCAL/$f" 2>/dev/null || md5sum "$CI_LOCAL/$f" | awk '{print $1}')
  if [ "$REMOTE_HASH" = "$LOCAL_HASH" ]; then
    echo "  未变化: $f"
  else
    tcp "$CI_LOCAL/$f" "$CI_DIR" && echo "  已更新: $f"
  fi
done

run_backend() {
  c_info "[2/3] 触发后端: 187 拉码 -> Maven 容器打包 -> 构建镜像 -> 重启 bzpt-server($SERVER_PORT)"
  tssh \
    "SERVER_IMG='$SERVER_IMG' SERVER_PORT='$SERVER_PORT' SNAIL_PORT='$SNAIL_PORT' JAVA_OPTS='$JAVA_OPTS' HOST_CONF_DIR='$HOST_CONF_DIR' HOST_LOG_DIR='$HOST_LOG_DIR' FORCE_CONF='${FORCE_CONF:-0}' bash $CI_DIR/build-backend.sh $BRANCH ${COMMIT:-}" < /dev/null
}

run_frontend() {
  c_info "[2/3] 触发前端: 187 拉码 -> Node 容器构建 -> 构建镜像 -> 重启 bzpt-web($WEB_PORT)"
  tssh \
    "WEB_IMG='$WEB_IMG' WEB_PORT='$WEB_PORT' PROD_API_TARGET='$PROD_API_TARGET' UPLOADS_TARGET='$UPLOADS_TARGET' bash $CI_DIR/build-frontend.sh $BRANCH ${COMMIT:-}" < /dev/null
}

case "$TARGET" in
  backend)  run_backend;;
  frontend) run_frontend;;
  all)      run_backend; run_frontend;;
esac

c_ok "测试环境发布流程结束: ${TARGET} ${BRANCH}${COMMIT:+ @ ${COMMIT}}"
