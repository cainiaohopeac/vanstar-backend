#!/bin/bash
# ============================================================
# 远端构建(CI)通用配置 —— 会被上传(比对 hash 后更新)到构建机 171 的 /data/deploy/ci/ci.env.sh
# 本文件只定义"服务端"的变量(目录/镜像/仓库地址/缓存卷), 不含任何本机路径。
# 集群相关的 IP/端口/镜像名由本机触发器 50-ci-deploy.sh 从现有 env.sh 以环境变量注入, 避免重复维护。
# ============================================================

# ---------- 目录 ----------
CI_DIR=${CI_DIR:-/data/deploy/ci}
SRC_DIR=${SRC_DIR:-/data/deploy/src}          # 代码检出目录(持久化, 后续增量 fetch)
BUILD_DIR=${BUILD_DIR:-/data/deploy/build}    # 构建上下文
LOG_DIR=${LOG_DIR:-/data/deploy/logs}         # 发布日志归档
REMOTE_DIR=${REMOTE_DIR:-/data/deploy}        # 与现有脚本保持一致的上传目录

# ---------- 仓库(只读 Deploy Key 方式) ----------
BACKEND_REPO=${BACKEND_REPO:-git@gitee.com:gols/bzpt-backend.git}
FRONTEND_REPO=${FRONTEND_REPO:-git@gitee.com:gols/bz-frontend.git}
BACKEND_SRC=$SRC_DIR/bzpt-backend
FRONTEND_SRC=$SRC_DIR/bz-frontend
DEPLOY_KEY=${DEPLOY_KEY:-$CI_DIR/deploy_key}

# ---------- 构建容器镜像(由 12-prepare-builder-images.sh 准备) ----------
GIT_IMG=${GIT_IMG:-bzpt-git:1.0}
MAVEN_IMG=${MAVEN_IMG:-maven:3.9-eclipse-temurin-17}
# 注: 本项目 packageManager 是 pnpm@11, 在 node:20 上会报 ERR_UNKNOWN_BUILTIN_MODULE, 因此用 22
NODE_IMG=${NODE_IMG:-node:22-alpine}
JAVA_BASE_DEFAULT=${JAVA_BASE_DEFAULT:-eclipse-temurin:17-jre-jammy}
NGINX_BASE_DEFAULT=${NGINX_BASE_DEFAULT:-nginx:stable-alpine}

# ---------- 依赖缓存卷(首次构建后会复用, 避免每次全量下载) ----------
M2_VOL=${M2_VOL:-bzpt-m2}                     # Maven ~/.m2
NPM_VOL=${NPM_VOL:-bzpt-pnpm-store}           # pnpm store
NODE_MODULES_VOL=${NODE_MODULES_VOL:-bzpt-node-modules}

# ---------- 其他 ----------
LOCK=${LOCK:-$CI_DIR/.deploy.lock}            # 多人并发发布锁
KEEP_LOGS=${KEEP_LOGS:-20}                    # 日志归档保留条数

# ---------- 通用函数 ----------
# 输出到 stderr: 避免像 $(...) 这类命令替换把进度信息吸进变量里
log() { echo "[$(date '+%F %T')] $*" >&2; }
die() { echo "[ERR] $*" >&2; exit 1; }

# git 操作统一走容器(服务端不装 git): git_run <工作目录> <git 参数...>
git_run() {
  local workdir="$1"; shift
  # 注意: bzpt-git 基于 node 镜像, 其 ENTRYPOINT 会拦截命令, 必须显式指定 --entrypoint git
  docker run --rm --entrypoint git \
    -e GIT_SSH_COMMAND="ssh -i /key -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR" \
    -v "$DEPLOY_KEY:/key:ro" -v "$SRC_DIR:/src" -w "$workdir" \
    "$GIT_IMG" "$@"
}

# 拉取/更新代码到指定分支与 commit: git_sync <仓库url> <目录名> <分支> [commit]
# 注意: 宿主机目录是 $SRC_DIR/$dir, 容器内同一目录是 /src/$dir(挂载点), 两者别混用
git_sync() {
  local repo="$1" dir="$2" branch="$3" commit="${4:-}"
  local target="$SRC_DIR/$dir" work="/src/$dir"
  if [ ! -d "$target/.git" ]; then
    log "首次检出 $repo -> $target"
    rm -rf "$target"; mkdir -p "$target"
    git_run "$work" clone "$repo" . || die "clone 失败: $repo(确认 Deploy Key 已添加到该仓库且已启用)"
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
