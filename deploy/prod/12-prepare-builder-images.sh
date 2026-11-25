#!/bin/bash
# ============================================================
# 步骤 1.5: 准备构建机 171 的"构建环境"(一次性)
#   1) 生成/确认只读 Deploy Key(公钥需添加到 Gitee 仓库 -> Deploy Keys)
#   2) 准备 maven / node / bzpt-git 三个构建容器镜像
#      - 有外网时直接用加速度源 docker pull
#      - 无外网时可加 --offline, 改用本机 tools/pull-base-image.mjs 下载后 scp 上传离线 load
# 用法: bash 12-prepare-builder-images.sh [--offline] [--force]
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

OFFLINE=0
FORCE=0
for a in "$@"; do
  case "$a" in
    --offline) OFFLINE=1;;
    --force)   FORCE=1;;
  esac
done

CI_DIR=/data/deploy/ci
MIRROR=docker.m.daocloud.io

# ---------- 1. Deploy Key ----------
c_info "[1/3] 检查构建机上的 Deploy Key"
rssh "$BUILD_HOST" "mkdir -p $CI_DIR"
KEY_DESC=$(rssh "$BUILD_HOST" "if [ -f $CI_DIR/deploy_key ]; then ssh-keygen -lf $CI_DIR/deploy_key.pub; else ssh-keygen -t ed25519 -N '' -C 'bzpt-ci-readonly' -f $CI_DIR/deploy_key >/dev/null; chmod 600 $CI_DIR/deploy_key; echo NEW; ssh-keygen -lf $CI_DIR/deploy_key.pub; fi" 2>&1 | tail -2)
PUB=$(rssh "$BUILD_HOST" "cat $CI_DIR/deploy_key.pub")
c_ok "Deploy Key 就绪"
echo
echo "  >>> 请把下面这行公钥添加到 Gitee 两个仓库的「仓库设置 -> Deploy Keys(只读)」:"
echo "      https://gitee.com/gols/bzpt-backend   /   https://gitee.com/gols/bz-frontend"
echo
echo "      $PUB"
echo
echo "      (fingerprint: $KEY_DESC)"
echo

# ---------- 2. 准备构建镜像 ----------
c_info "[2/3] 准备构建容器镜像: node:20-alpine / maven:3.9-eclipse-temurin-17"

prepare_one() {   # prepare_one <目标镜像名> <mirror 前缀路径>
  local img="$1" ref="$2"
  if [ "$FORCE" = "0" ]; then
    rssh "$BUILD_HOST" "docker image inspect '$img' >/dev/null 2>&1" 2>/dev/null && { echo "  已存在: $img"; return 0; }
  fi
  if [ "$OFFLINE" = "1" ]; then
    c_info "  离线下载 $img"
    local stage="$STAGE_DIR"; mkdir -p "$stage"
    (cd "$PROD_DIR/tools" && node pull-base-image.mjs "$ref" --tar "$stage/builder-$(basename "$img").tar.gz" --load-tag "$img")
    rcp "$stage/builder-$(basename "$img").tar.gz" "$BUILD_HOST" "$REMOTE_DIR"
    rssh "$BUILD_HOST" "docker load -i $REMOTE_DIR/builder-$(basename "$img").tar.gz; rm -f $REMOTE_DIR/builder-$(basename "$img").tar.gz"
  else
    echo "  pull $ref"
    rssh "$BUILD_HOST" "docker pull '$MIRROR/$ref' >/dev/null 2>&1 || echo PULL_FAIL"
    # tr -d '\r': 远端输出到 Windows 侧可能带 CR, 会让 grep -x 匹配失败
    if rssh "$BUILD_HOST" "docker images --format '{{.Repository}}:{{.Tag}}' | tr -d '\r' | grep -qx '$MIRROR/$ref'"; then
      rssh "$BUILD_HOST" "docker tag '$MIRROR/$ref' '$img'"
      echo "  tagged -> $img"
    else
      c_warn "  $img 拉取失败(可改用 --offline)"
      return 1
    fi
  fi
}

prepare_one node:22-alpine library/node:22-alpine || true
prepare_one maven:3.9-eclipse-temurin-17 library/maven:3.9-eclipse-temurin-17 || true

# ---------- 3. bzpt-git: node 镜像 + git/ssh(服务端不装任何东西) ----------
c_info "[3/3] 构建 bzpt-git:1.0(based on node:20-alpine + git + openssh-client)"
mkdir -p "$STAGE_DIR"
cat > "$STAGE_DIR/git.Dockerfile" <<'EOF'
FROM node:20-alpine
RUN apk add --no-cache git openssh-client
EOF
rssh "$BUILD_HOST" "mkdir -p /data/deploy/build/gitctx"
rcp "$STAGE_DIR/git.Dockerfile" "$BUILD_HOST" "/data/deploy/build/gitctx"
rssh "$BUILD_HOST" "cd /data/deploy/build/gitctx && docker build -f git.Dockerfile -t bzpt-git:1.0 . 2>&1 | tail -3"

c_info "构建环境清单:"
rssh "$BUILD_HOST" "docker images --format '  {{.Repository}}:{{.Tag}}  {{.Size}}' | grep -E 'node:|maven:|bzpt-git|temurin|nginx:'"
c_ok "构建机环境准备完成"
