#!/bin/bash
# ============================================================
# 步骤 3.1: 准备业务镜像的基础镜像(JDK17 / nginx)
#   本地拉取 -> 打包上传 192.168.0.171 -> docker load -> tag 别名
# 说明:
#   - 不在服务器上 pull(服务器只做离线载入), 本地也没有 docker 时
#     自动改用 tools/pull-base-image.mjs 直连 registry 下载
#   - 后端 JDK17 已弃用原 bzpt-java17(liberica), 默认改用 eclipse-temurin:17-jre-jammy
# 用法:
#   bash 11-prepare-base-images.sh                # java + nginx
#   bash 11-prepare-base-images.sh java           # 只准备 JDK17
#   bash 11-prepare-base-images.sh nginx
#   REGISTRY=docker.m.daocloud.io bash 11-prepare-base-images.sh   # 走国内镜像站
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

# 默认只准备 JDK17: 171 上已有 nginx 镜像, 前端构建直接用(需要重新导入时执行 bash 11-prepare-base-images.sh nginx)
ROLE=${1:-java}
FORCE_PULL=0
for a in "$@"; do [ "$a" = "--force-pull" ] && FORCE_PULL=1; done
require_cmd ssh
require_cmd tar
command -v node >/dev/null 2>&1 || die "缺少 node(pull-base-image.mjs 需要), 或本机安装 docker CLI 亦可"

mkdir -p "$STAGE_DIR"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

# ---------- 远程载入脚本 ----------
cat > "$TMP/load-base.sh" <<'EOS'
set -euo pipefail
ROLE=$1        # java / nginx
LOADED=$2      # 载入后的镜像名(如 library/eclipse-temurin:17-jre-jammy)
ALIAS=$3       # 业务 Dockerfile 里引用的别名(如 bzpt-java17:17.0.11-cds)
TAR=$4
D=/data/deploy
cd "$D"
echo "-- docker load $TAR --"
docker load -i "$TAR"
docker tag "$LOADED" "$ALIAS"
echo "  $LOADED -> $ALIAS"
docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep -Ei 'eclipse-temurin|openjdk|jdk|nginx' || true
EOS

# 记录解析结果到 171 的 base-images.env, 供 30/31 构建时使用
record() {
  local role="$1" img="$2"
  rssh "$BUILD_HOST" "mkdir -p $REMOTE_DIR; grep -v '^RESOLVED_${role}_IMG=' $REMOTE_DIR/base-images.env 2>/dev/null > $REMOTE_DIR/base-images.env.tmp || true; echo 'RESOLVED_${role}_IMG=$img' >> $REMOTE_DIR/base-images.env.tmp; mv $REMOTE_DIR/base-images.env.tmp $REMOTE_DIR/base-images.env"
}

prepare() {
  local role="$1" pull="$2" alias="$3" skip_if_exists="${4:-}"
  local tarname="base-$role.tar.gz"
  local tar="$STAGE_DIR/$tarname"
  local loaded=""

  c_info "==== 准备 $role 基础镜像: $pull ===="
  # 构建机已有该镜像则直接复用(如 171 上已有的 nginx), 不再重复拉取上传
  if [ "$skip_if_exists" = "skip-if-exists" ] && rssh "$BUILD_HOST" "docker image inspect '$alias' >/dev/null 2>&1"; then
    c_info "  $BUILD_HOST 上已存在 $alias, 跳过拉取"
    record "$role" "$alias"
    c_ok "$role 基础镜像就绪(复用 $alias)"
    return
  fi
  # 载入后的镜像名: 官方镜像会补 library/ 前缀(与 pull-base-image.mjs 输出一致)
  case "$pull" in
    */*) loaded="$pull";;
    *)   loaded="library/$pull";;
  esac

  if [ -f "$tar" ] && [ "$FORCE_PULL" != "1" ]; then
    c_info "[1/3] 复用已下载的基础镜像包: $tar(需重新拉取加 --force-pull)"
  elif command -v docker >/dev/null 2>&1; then
    c_info "[1/3] 本地 docker pull ..."
    docker pull "$pull"
    docker save "$pull" | gzip -1 > "$tar"
  else
    c_info "[1/3] 本地无 docker, 用 registry 直连工具下载 ..."
    loaded=$(node "$PULL_TOOL" --image "$pull" --registry "$REGISTRY" --out "$tar" | tail -1)
    [ -n "$loaded" ] || die "拉取失败: $pull"
  fi
  ls -lh "$tar" | awk '{print "      包大小: "$5}'

  c_info "[2/3] 上传到构建机 $BUILD_HOST ..."
  rssh "$BUILD_HOST" "mkdir -p $REMOTE_DIR"
  rcp "$tar" "$BUILD_HOST" "$REMOTE_DIR"

  c_info "[3/3] 在 $BUILD_HOST 上载入并 tag 为 $alias ..."
  rssh "$BUILD_HOST" "bash -s" < "$TMP/load-base.sh" "$role" "$loaded" "$alias" "$tarname" \
    && record "$role" "$loaded" \
    && c_ok "$role 基础镜像就绪: $loaded (别名 $alias)" || die "$role 基础镜像载入失败"
}

case "$ROLE" in
  all)
    prepare java  "$JAVA_PULL_IMG"  "$JAVA_BASE_IMG"
    prepare nginx "$NGINX_PULL_IMG" "$NGINX_BASE_IMG" skip-if-exists
    ;;
  java)  prepare java  "$JAVA_PULL_IMG"  "$JAVA_BASE_IMG";;
  nginx) prepare nginx "$NGINX_PULL_IMG" "$NGINX_BASE_IMG" skip-if-exists;;
  *) die "未知参数: $ROLE (all|java|nginx)";;
esac

c_ok "基础镜像准备完成(如需换源: REGISTRY=镜像站 JAVA_PULL_IMG=镜像:tag bash 11-prepare-base-images.sh)"
