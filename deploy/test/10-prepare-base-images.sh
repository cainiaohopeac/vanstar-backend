#!/bin/bash
# ============================================================
# 测试环境一次性准备: 依赖镜像从生产构建机 171 迁移到测试机 187
#   187 不联网拉镜像, 全部依赖镜像复用生产环境的(与生产 tag 完全一致):
#     maven:3.9-eclipse-temurin-17        Maven 构建容器
#     node:22-alpine                      前端构建容器(packageManager 是 pnpm@11, 必须 node 22)
#     bzpt-git:1.0                        git 容器(187 不装 git, 拉码全容器化)
#     eclipse-temurin:17-jre-jammy        后端运行 JDK 基础镜像
#                                        并在 187 上 tag 出别名 bzpt-java17:17.0.11-cds
#     nginx:stable-alpine                 前端运行基础镜像
#   同时把生产 171 的只读 Deploy Key 复制到 187(仓库已授权该公钥, 无需再去 Gitee 配置)。
# 用法(本机任意目录):
#   bash 10-prepare-base-images.sh            # 迁移全部缺失镜像 + deploy_key
#   bash 10-prepare-base-images.sh list       # 只查看两端镜像差异
#   bash 10-prepare-base-images.sh maven:3.9-eclipse-temurin-17   # 只迁移指定镜像
# 未配免密时: BZPT_SSH_PASS='密码' bash 10-prepare-base-images.sh
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

# 171 上的 deploy_key 与 base 镜像记录
PROD_CI_DIR=${PROD_CI_DIR:-/data/deploy/ci}
PROD_KEY=/data/deploy/ci/deploy_key

MODE=${1:-migrate}
IMGS=$BASE_IMAGES
[ "$MODE" = "list" ] && { IMGS=""; }
case "$MODE" in
  list) ;;
  m*) ;;
  *) IMGS="$MODE";;   # 指定镜像名
esac

# ---------- 0. 前置检查 ----------
command -v ssh >/dev/null 2>&1 || die "需要 ssh"
[ -n "$SSH_PASS" ] && command -v sshpass >/dev/null 2>&1 || true

c_info "检查两端镜像现状..."
tssh "docker images --format '{{.Repository}}:{{.Tag}}' | sort" > /tmp/test-hosts-imgs.$$
_auth ssh $SSH_OPT "$SSH_USER@$PROD_BUILD_HOST" \
  "docker images --format '{{.Repository}}:{{.Tag}}' | sort" > /tmp/prod-imgs.$$
trap 'rm -f /tmp/test-hosts-imgs.$$ /tmp/prod-imgs.$$' EXIT

echo "--- 生产 171 ---"; cat /tmp/prod-imgs.$$
echo "--- 测试 187 ---"; cat /tmp/test-hosts-imgs.$$

# ---------- 1. 迁移镜像: 171 save | 本机流式中转 | 187 load ----------
migrate() {
  local img="$1"
  if grep -Fqx "$img" /tmp/test-hosts-imgs.$$; then
    c_ok "已存在, 跳过: $img"
    return 0
  fi
  if ! grep -Fqx "$img" /tmp/prod-imgs.$$; then
    die "生产 171 上没有镜像: $img (先在生产执行 11/12 号脚本准备)"
  fi
  c_info "迁移 $img : 171 -> 本机流式 -> 187 (镜像较大, 请耐心等待)..."
  _auth ssh $SSH_OPT "$SSH_USER@$PROD_BUILD_HOST" "docker save $img | gzip -1" \
    | tssh "gzip -d | docker load"
  grep -Fqx "$img" <(tssh "docker images --format '{{.Repository}}:{{.Tag}}'") \
    || die "迁移后未在 187 上找到 $img, 请手动检查"
  c_ok "已迁移: $img"
}

[ -n "$IMGS" ] && for img in $IMGS; do migrate "$img"; done

# JDK 运行镜像别名(Dockerfile 默认 FROM bzpt-java17:17.0.11-cds)
if tssh "docker image inspect eclipse-temurin:17-jre-jammy >/dev/null 2>&1 \
         && ! docker image inspect $JAVA_BASE_ALIAS >/dev/null 2>&1"; then
  c_info "打别名: eclipse-temurin:17-jre-jammy -> $JAVA_BASE_ALIAS"
  tssh "docker tag eclipse-temurin:17-jre-jammy $JAVA_BASE_ALIAS"
fi

# ---------- 2. 复制 Deploy Key(生产 171 -> 测试 187, 公钥已授权两个仓库) ----------
if tssh "[ -f $CI_DIR/deploy_key ]"; then
  c_ok "187 已有 deploy_key, 跳过"
else
  c_info "复制 Deploy Key: 171 -> 本机(临时) -> 187 ..."
  TMP_KEY=$(mktemp)
  trap 'rm -f /tmp/test-hosts-imgs.$$ /tmp/prod-imgs.$$ "$TMP_KEY"' EXIT
  _auth scp $SSH_OPT "$SSH_USER@$PROD_BUILD_HOST:$PROD_KEY" "$TMP_KEY" >/dev/null
  tssh "mkdir -p $CI_DIR"
  _auth scp $SSH_OPT "$TMP_KEY" "$SSH_USER@$TEST_HOST:$CI_DIR/deploy_key" >/dev/null
  tssh "chmod 600 $CI_DIR/deploy_key"
  rm -f "$TMP_KEY"
  c_ok "deploy_key 已就位: 187:$CI_DIR/deploy_key"
fi

# ---------- 3. 落盘已解析镜像记录(供 build-backend/build-frontend 读取) ----------
tssh "mkdir -p $REMOTE_DIR
cat > $BASE_ENV_FILE <<EOF
RESOLVED_JAVA_IMG=$JAVA_BASE_ALIAS
RESOLVED_NGINX_IMG=nginx:stable-alpine
EOF"
c_ok "基础镜像记录: 187:$BASE_ENV_FILE"

# ---------- 4. 磁盘提示 ----------
DISK_FREE=$(tssh "df -BG / | awk 'NR==2{print \$4}' | tr -d 'G'")
if [ "${DISK_FREE:-0}" -lt 15 ]; then
  c_warn "187 根盘仅剩 ${DISK_FREE}G, 构建镜像 + Maven 依赖卷约需 3~5G; 建议清理:"
  c_warn "  tssh: docker image prune -f   # 清理悬挂镜像(187 目前有多个 <none>)"
fi

c_ok "测试环境依赖准备完成"
