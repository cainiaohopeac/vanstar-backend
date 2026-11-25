#!/bin/bash
# ============================================================
# 前端远端构建 + 发布(在测试机 187 上执行, 由本机 deploy/test/50-ci-deploy.sh 触发)
# 流程: 拉代码(容器 git) -> Node 容器装依赖并构建 dist -> docker build(nginx 基础镜像用生产的)
#       -> 覆盖重启 bzpt-web(8088) + 健康检查
# 入参: $1=分支(默认 master-1030)  $2=commit(可选)
# 环境变量(由触发器注入): WEB_IMG WEB_PORT PROD_API_TARGET UPLOADS_TARGET
# ============================================================
set -euo pipefail
CI_ENV=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd)/ci.env.sh
[ -f "$CI_ENV" ] || CI_ENV=/root/bzpt-deploy/ci/ci.env.sh
source "$CI_ENV"

BRANCH=${1:-master-1030}
COMMIT=${2:-}
WEB_IMG=${WEB_IMG:?需传入 WEB_IMG, 例 bzpt-web:5.X}
WEB_PORT=${WEB_PORT:-8088}
PROD_API_TARGET=${PROD_API_TARGET:-http://127.0.0.1:8081/}
UPLOADS_TARGET=${UPLOADS_TARGET:-http://127.0.0.1:8081}
ROLLBACK=${ROLLBACK:-0}

mkdir -p "$LOG_DIR" "$BUILD_DIR/frontend"
RUN_LOG=$LOG_DIR/frontend-$(date +%Y%m%d-%H%M%S).log
exec > >(tee "$RUN_LOG") 2>&1

ci_lock
log "===== [测试] 前端发布开始: 分支=$BRANCH ${COMMIT:+commit=$COMMIT} ====="

if [ "$ROLLBACK" = "1" ]; then
  [ -n "$COMMIT" ] || die "回滚必须指定 commit: bash 51-rollback.sh frontend <短commit>"
  SHA=$COMMIT
  docker image inspect "${WEB_IMG%:*}:$SHA" >/dev/null 2>&1 \
    || die "187 上没有 $WEB_IMG@$SHA 的历史镜像, 请确认该 commit 曾被构建过"
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
cd "$FRONTEND_SRC"
# 187 到部分镜像源(如华为云)带宽很低, 默认显式指定 npmmirror + 降低并发 + 放长超时,
# 避免 pnpm 报 "The operation was aborted due to timeout"。仓库 .npmrc 里的 registry 会被 --registry 覆盖。
PNPM_REGISTRY=${PNPM_REGISTRY:-https://registry.npmmirror.com}
PNPM_CONCURRENCY=${PNPM_CONCURRENCY:-4}
REG_ARG=""
[ -n "$PNPM_REGISTRY" ] && REG_ARG="--registry=$PNPM_REGISTRY"
log "[2/4] Node 构建 dist(容器 $NODE_IMG, 依赖卷 $NODE_MODULES_VOL, 源 $PNPM_REGISTRY)"
NODE_HEAP=${NODE_HEAP:---max-old-space-size=4096}
if docker run --rm -v "$FRONTEND_SRC:/src" -v "$NODE_MODULES_VOL:/src/node_modules" \
     -v "$NPM_VOL:/root/.local/share/pnpm/store" -w /src \
     -e NODE_OPTIONS="$NODE_HEAP" -e COREPACK_ENABLE_STRICT=0 \
     -e COREPACK_NPM_REGISTRY="${COREPACK_NPM_REGISTRY:-https://registry.npmmirror.com}" \
     "$NODE_IMG" sh -c "corepack enable >/dev/null 2>&1 || true; corepack pnpm install --no-frozen-lockfile $REG_ARG --network-concurrency=$PNPM_CONCURRENCY --fetch-timeout=600000 && corepack pnpm run build:prod"; then
  log "       dist 构建完成"
else
  die "前端构建失败(见上方输出)"
fi
[ -d "$FRONTEND_SRC/dist" ] || die "dist 目录未生成"

# ---------- 3. 构建镜像 ----------
log "[3/4] 构建镜像 $WEB_IMG (并打标签 $SHA)"
[ -f "$REMOTE_DIR/base-images.env" ] && . "$REMOTE_DIR/base-images.env"
NGINX_BASE=${RESOLVED_NGINX_IMG:-${NGINX_BASE_DEFAULT:-nginx:stable-alpine}}
log "       nginx 基础镜像: $NGINX_BASE"
docker build --build-arg NGINX_BASE_IMG="$NGINX_BASE" \
  -t "$WEB_IMG" -t "${WEB_IMG%:*}:$SHA" "$FRONTEND_SRC" 2>&1 | tail -5
fi

# ---------- 4. 覆盖重启容器并校验 ----------
log "[4/4] 重启前端容器 bzpt-web($WEB_PORT)"
docker rm -f bzpt-web >/dev/null 2>&1 || true
docker run -d --name bzpt-web --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e PROD_API_TARGET="$PROD_API_TARGET" \
  -e UPLOADS_TARGET="$UPLOADS_TARGET" \
  "$WEB_IMG" >/dev/null
sleep 3
# 仅看 HTTP 200 会误判: 代理未命中时 fallback 到 index.html 也是 200,
# 代理通了但路径错(如多带 /prod-api)时后端返回 {"code":404}。因此校验响应体 code=200。
resp=$(curl -s -m 5 "http://127.0.0.1:$WEB_PORT/prod-api/auth/tenant/list" 2>/dev/null || true)
case "$resp" in
  *'"code":200'*) log "       代理校验通过: /prod-api/auth/tenant/list -> code=200";;
  *) log "       [WARN] /prod-api 代理异常: ${resp:0:160}"; log "              当前目标 $PROD_API_TARGET (单机应为 http://127.0.0.1:${SERVER_PORT:-8081}/)";;
esac
code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$WEB_PORT/" || true)
[ "$code" = "200" ] || { docker logs --tail 40 bzpt-web; die "前端容器启动异常"; }
log "       前端首页: http://127.0.0.1:$WEB_PORT/ -> 200"
docker ps --format '{{.Names}}|{{.Status}}' | grep bzpt-web || true

ls -1t "$LOG_DIR"/frontend-*.log 2>/dev/null | tail -n +$((KEEP_LOGS + 1)) | xargs -r rm -f
log "===== [测试] 前端发布完成: $WEB_IMG @ $SHA (日志: $RUN_LOG) ====="
