#!/bin/bash
# ============================================================
# 测试环境远端构建(CI)服务端配置 —— 由本机 50-ci-deploy.sh 比对 hash 后
# 上传到测试机 187 的 /root/bzpt-deploy/ci/ci.env.sh
# 只定义"服务端"变量(目录/镜像/仓库地址/缓存卷), 集群相关参数由触发器注入。
# 与生产 ci.env.sh 的差异仅在目录与默认值; git_run/git_sync/ci_lock 逻辑一致。
# ============================================================

# ---------- 目录 ----------
CI_DIR=${CI_DIR:-/root/bzpt-deploy/ci}
SRC_DIR=${SRC_DIR:-/root/bzpt-deploy/src}
BUILD_DIR=${BUILD_DIR:-/root/bzpt-deploy/build}
LOG_DIR=${LOG_DIR:-/root/bzpt-deploy/logs}
REMOTE_DIR=${REMOTE_DIR:-/root/bzpt-deploy}

# ---------- 仓库(只读 Deploy Key 方式, 密钥由 10-prepare-base-images.sh 从 171 复制) ----------
BACKEND_REPO=${BACKEND_REPO:-git@gitee.com:gols/bzpt-backend.git}
FRONTEND_REPO=${FRONTEND_REPO:-git@gitee.com:gols/bz-frontend.git}
BACKEND_SRC=$SRC_DIR/bzpt-backend
FRONTEND_SRC=$SRC_DIR/bz-frontend
DEPLOY_KEY=${DEPLOY_KEY:-$CI_DIR/deploy_key}

# ---------- 构建容器镜像(与生产完全同名同 tag, 由 10-prepare-base-images.sh 从 171 迁移) ----------
GIT_IMG=${GIT_IMG:-bzpt-git:1.0}
MAVEN_IMG=${MAVEN_IMG:-maven:3.9-eclipse-temurin-17}
# 注: 本项目 packageManager 是 pnpm@11, 在 node:20 上会报 ERR_UNKNOWN_BUILTIN_MODULE, 因此用 22
NODE_IMG=${NODE_IMG:-node:22-alpine}
JAVA_BASE_DEFAULT=${JAVA_BASE_DEFAULT:-bzpt-java17:17.0.11-cds}
NGINX_BASE_DEFAULT=${NGINX_BASE_DEFAULT:-nginx:stable-alpine}

# ---------- 依赖缓存卷(首次构建后复用, 避免每次全量下载) ----------
M2_VOL=${M2_VOL:-bzpt-test-m2}
NPM_VOL=${NPM_VOL:-bzpt-test-pnpm-store}
NODE_MODULES_VOL=${NODE_MODULES_VOL:-bzpt-test-node-modules}

# ---------- 其他 ----------
LOCK=${LOCK:-$CI_DIR/.deploy.lock}
KEEP_LOGS=${KEEP_LOGS:-20}

# ---------- 通用函数 ----------
log() { echo "[$(date '+%F %T')] $*" >&2; }
die() { echo "[ERR] $*" >&2; exit 1; }

# git 操作统一走容器(187 不装 git): git_run <工作目录> <git 参数...>
git_run() {
  local workdir="$1"; shift
  docker run --rm --entrypoint git \
    -e GIT_SSH_COMMAND="ssh -i /key -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR" \
    -v "$DEPLOY_KEY:/key:ro" -v "$SRC_DIR:/src" -w "$workdir" \
    "$GIT_IMG" "$@"
}

# 拉取/更新代码到指定分支与 commit: git_sync <仓库url> <目录名> <分支> [commit]
git_sync() {
  local repo="$1" dir="$2" branch="$3" commit="${4:-}"
  local target="$SRC_DIR/$dir" work="/src/$dir"
  if [ ! -d "$target/.git" ]; then
    log "首次检出 $repo -> $target"
    rm -rf "$target"; mkdir -p "$target"
    git_run "$work" clone "$repo" . || die "clone 失败: $repo(确认 deploy_key 已就位且公钥已添加到该仓库)"
  fi
  git_run "$work" fetch --all --prune --tags 2>&1 | tail -2 || die "fetch 失败: $repo"
  if [ -n "$commit" ]; then
    git_run "$work" reset --hard "$commit" >/dev/null 2>&1 || die "不存在该 commit: $commit"
  else
    git_run "$work" reset --hard "origin/$branch" >/dev/null 2>&1 || die "不存在远端分支: $branch"
  fi
  git_run "$work" rev-parse --short HEAD
}

# 发布锁(避免多人同时构建互相覆盖)
ci_lock() {
  mkdir -p "$(dirname "$LOCK")"
  exec 9>"$LOCK"
  flock -w 900 9 || die "已有其他发布在进行中(锁: $LOCK)"
}
