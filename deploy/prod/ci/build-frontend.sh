#!/bin/bash
# ============================================================
# 前端远端构建 + 发布(在构建机 171 上执行, 由本机 50-ci-deploy.sh 触发)
# 流程: 拉代码(容器 git) -> Node 容器装依赖并构建 dist -> docker build(nginx 基础镜像取自 base-images.env)
#       -> 重启 bzpt-web 容器(8089) -> 健康检查 -> 网关可达校验
# 入参: $1=分支(默认 master)  $2=commit(可选)
# 环境变量(由触发器注入): WEB_IMG WEB_PORT GW_PORT REMOTE_DIR SSH_USER
# ============================================================
set -euo pipefail
CI_ENV=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd)/ci.env.sh
[ -f "$CI_ENV" ] || CI_ENV=/data/deploy/ci/ci.env.sh
source "$CI_ENV"

BRANCH=${1:-master}
COMMIT=${2:-}
WEB_IMG=${WEB_IMG:?需传入 WEB_IMG, 例 bzpt-web:5.X}
WEB_PORT=${WEB_PORT:-8089}
GW_PORT=${GW_PORT:-8088}
REMOTE_DIR=${REMOTE_DIR:-/data/deploy}

mkdir -p "$LOG_DIR" "$BUILD_DIR/frontend"
RUN_LOG=$LOG_DIR/frontend-$(date +%Y%m%d-%H%M%S).log
exec > >(tee "$RUN_LOG") 2>&1

ci_lock
log "===== 前端发布开始: 分支=$BRANCH ${COMMIT:+commit=$COMMIT} ====="

ROLLBACK=${ROLLBACK:-0}
if [ "$ROLLBACK" = "1" ]; then
  [ -n "$COMMIT" ] || die "回滚必须指定 commit: bash 51-rollback.sh frontend <commit>"
  SHA=$COMMIT
  docker image inspect "${WEB_IMG%:*}:$SHA" >/dev/null 2>&1 \
    || die "构建机上没有 $WEB_IMG@$SHA 的历史镜像, 请先确认该 commit 曾被构建过"
  docker tag "${WEB_IMG%:*}:$SHA" "$WEB_IMG"
  log "回滚模式: $WEB_IMG -> $SHA(复用已有镜像, 跳过拉码/构建)"
  jump_to=4
else
  jump_to=1
fi

if [ "$jump_to" -le 1 ]; then
# ---------- 1. 拉代码 ----------
log "[1/4] 检出代码 $FRONTEND_REPO"
SHA=$(git_sync "$FRONTEND_REPO" bz-frontend "$BRANCH" "$COMMIT" | tail -1 | tr -d '\r\n')
case "$SHA" in
  [0-9a-f]*[0-9a-f]) [ ${#SHA} -le 40 ] || die "无法解析代码版本号: $SHA";;
  *) die "无法解析代码版本号: $SHA";;
esac
log "       代码版本: $SHA"

# ---------- 2. Node 构建 dist ----------
FRONTEND_SRC_PATH=$FRONTEND_SRC
cd "$FRONTEND_SRC_PATH"
if [ -f pnpm-lock.yaml ]; then
  # 用 corepack 读取 package.json 的 packageManager 字段自动匹配版本(本项目是 pnpm@11, 固定装 9 会解析失败)
  PKG=corepack_pnpm; PRE="corepack enable >/dev/null 2>&1 || true"
elif [ -f package-lock.json ]; then
  PKG=npm; PRE="true"
else
  PKG=corepack_pnpm; PRE="corepack enable >/dev/null 2>&1 || true"
fi
log "[2/4] Node 构建 dist(容器 $NODE_IMG, 依赖卷 $NODE_MODULES_VOL, 包管理器 $PKG)"
# 服务端构建不受个人机器内存限制, 默认给 8GB 堆
NODE_HEAP=${NODE_HEAP:---max-old-space-size=8192}
if [ "$PKG" = "corepack_pnpm" ]; then
  INSTALL_CMD="corepack pnpm install --no-frozen-lockfile"
  BUILD_CMD="corepack pnpm run build:prod"
else
  INSTALL_CMD="$PKG install --no-frozen-lockfile"
  BUILD_CMD="$PKG run build:prod"
fi
# corepack 拉 pnpm 的元数据/包接口在 npmmirror 上偶发 HTTP 500，且构建机无法直连官方源。
# 兜底：构建机 /data/deploy/ci/corepack-home 预置了 corepack 缓存（pnpm@11.8.0 已装好，
# 含 .corepack 短路文件）——挂载为 COREPACK_HOME 后 corepack 直接复用本地缓存，零联网。
# 缓存缺失时自动回退联网下载（行为与旧版一致）
frontend_build_once() {
  local cpargs=""
  if [ -d "$CI_DIR/corepack-home/v1" ]; then
    cpargs="-v $CI_DIR/corepack-home:/cp -e COREPACK_HOME=/cp"
    log "       使用预置 corepack 缓存($CI_DIR/corepack-home, pnpm 离线可用)"
  fi
  docker run --rm -v "$FRONTEND_SRC_PATH:/src" -v "$NODE_MODULES_VOL:/src/node_modules" \
     -v "$NPM_VOL:/root/.local/share/pnpm/store" $cpargs -w /src \
     -e NODE_OPTIONS="$NODE_HEAP" -e COREPACK_ENABLE_STRICT=0 \
     -e COREPACK_NPM_REGISTRY="${COREPACK_NPM_REGISTRY:-https://registry.npmmirror.com}" \
     "$NODE_IMG" sh -c "$PRE && $INSTALL_CMD && $BUILD_CMD"
}
if frontend_build_once "${COREPACK_NPM_REGISTRY:-https://registry.npmmirror.com}"; then
  log "       dist 构建完成"
else
  die "前端构建失败(见上方输出)"
fi
[ -d "$FRONTEND_SRC_PATH/dist" ] || die "dist 目录未生成"

# ---------- 3. 构建镜像 ----------
log "[3/4] 构建镜像 $WEB_IMG (并打标签 $SHA)"
[ -f "$REMOTE_DIR/base-images.env" ] && . "$REMOTE_DIR/base-images.env"
NGINX_BASE=${RESOLVED_NGINX_IMG:-${NGINX_BASE_DEFAULT:-nginx:stable-alpine}}
log "       nginx 基础镜像: $NGINX_BASE"
docker build --build-arg NGINX_BASE_IMG="$NGINX_BASE" \
  -t "$WEB_IMG" -t "${WEB_IMG%:*}:$SHA" "$FRONTEND_SRC_PATH" 2>&1 | tail -5
fi

# ---------- 4. 重启容器并校验 ----------
log "[4/4] 重启前端容器 bzpt-web($WEB_PORT)"
docker rm -f bzpt-web >/dev/null 2>&1 || true
docker run -d --name bzpt-web --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e PROD_API_TARGET="http://127.0.0.1:$GW_PORT/prod-api/" \
  -e UPLOADS_TARGET="http://127.0.0.1:$GW_PORT" \
  "$WEB_IMG" >/dev/null
sleep 3
code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$WEB_PORT/" || true)
log "       容器内探测: HTTP $code"
[ "$code" = "200" ] || { docker logs --tail 40 bzpt-web; die "前端容器启动异常"; }
GW_CODE=$(curl -s -m 8 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$GW_PORT/" || echo 000)
if [ "$GW_CODE" = "200" ]; then
  log "       网关 $GW_PORT 返回 200"
else
  log "       [WARN] 网关 $GW_PORT 返回 $GW_CODE(检查 nginx 是否已加载 nginx-gw.conf)"
fi
docker ps --format '{{.Names}}|{{.Status}}' | grep bzpt-web

ls -1t "$LOG_DIR"/frontend-*.log 2>/dev/null | tail -n +$((KEEP_LOGS + 1)) | xargs -r rm -f
log "===== 前端发布完成: $WEB_IMG @ $SHA (日志: $RUN_LOG) ====="
