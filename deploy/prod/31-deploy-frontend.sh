#!/bin/bash
# ============================================================
# 步骤 7: 发布前端 -> 接入层 192.168.0.171(bzpt-web, 内部 8089, 由网关 8088 对外)
# 流程(本地不依赖 docker):
#   1) 本地 vite 构建 dist, 与 Dockerfile/nginx.conf/entrypoint 打成上下文包
#   2) 上传到构建机 171, 在 171 上 docker build(nginx 基础镜像由 11 脚本载入)
#   3) 在 171 本机启动容器(host 网络 8089)并健康检查
# 用法:
#   bash 31-deploy-frontend.sh            # 用现有 node_modules 构建
#   bash 31-deploy-frontend.sh --install  # 强制安装依赖
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

[ -d "$FRONTEND_DIR" ] || die "前端目录不存在: $FRONTEND_DIR
  若前端仓库不在本仓库同级目录, 用 FRONTEND_DIR=/path/to/bz-frontend bash 31-deploy-frontend.sh 指定"
DO_INSTALL=0
[ "${1:-}" = "--install" ] && DO_INSTALL=1
CTX_TAR=frontend-ctx.tar.gz
WEB_IMG_NAME=${WEB_IMG%:*}
WEB_TAG=${WEB_IMG##*:}

# ---------- vite 构建堆上限(按可用内存自适应) ----------
# 实测: 2GB 必崩(模块图 3275 个, three/echarts/vxe-table 全量引入), 4GB 可稳定通过;
#       构建总内存峰值约 2.5-3GB = vite 主线程(受本限制) + esbuild 子进程(Go 进程, 不受本限制)
# 所以不能无脑往大了给: 8GB 机器上给 6GB 堆 + esbuild 子进程 + 系统/IDE, 很容易把物理内存打满,
# 反而必然 OOM(报错同样是 "JavaScript heap out of memory", 其实是系统没内存可提交)
# 覆盖方式: NODE_OPTIONS="--max-old-space-size=8192" bash 31... ; 想完全交给 V8 默认: NODE_OPTIONS= bash 31...
if [ -z "${NODE_OPTIONS+x}" ]; then
  avail_mb() {
    if [ -r /proc/meminfo ]; then
      local kb; kb=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo 2>/dev/null)
      [ -n "$kb" ] && { echo $((kb / 1024)); return; }
    fi
    if command -v sysctl >/dev/null 2>&1; then
      local b; b=$(sysctl -n hw.memsize 2>/dev/null)      # macOS 只有总内存, 作近似值
      [ -n "$b" ] && { echo $((b / 1024 / 1024)); return; }
    fi
    echo 0                                            # 探测不到(如 Windows Git Bash)
  }
  AVAIL_MB=$(avail_mb)
  if [ "$AVAIL_MB" -ge 6144 ]; then
    NODE_OPTIONS="--max-old-space-size=4096"
  elif [ "$AVAIL_MB" -ge 4096 ]; then
    NODE_OPTIONS="--max-old-space-size=3072"
    c_warn "可用内存仅 ${AVAIL_MB}MB, 堆上限降到 3GB; 若仍 OOM 请先关闭 IDE/浏览器等占内存程序"
  elif [ "$AVAIL_MB" -gt 0 ]; then
    NODE_OPTIONS="--max-old-space-size=2048"
    c_warn "可用内存仅 ${AVAIL_MB}MB, 前端构建大概率 OOM(实测需约 3GB), 建议先释放内存或换内存充足的机器"
  else
    NODE_OPTIONS="--max-old-space-size=4096"
    c_warn "无法探测可用内存, 按 4GB 堆构建; 构建总内存峰值约 3GB, 请确保机器可用内存 >= 6GB"
  fi
fi
export NODE_OPTIONS

# ---------- 1. 本地构建 dist ----------
c_info "[1/4] 构建前端静态资源(本地 $FRONTEND_DIR, NODE_OPTIONS=$NODE_OPTIONS)"
cd "$FRONTEND_DIR"
if [ ! -d node_modules ] || [ "$DO_INSTALL" = "1" ]; then
  if command -v pnpm >/dev/null 2>&1; then
    pnpm install --no-frozen-lockfile
  else
    npm install --no-audit --no-fund
  fi
fi
if [ -x node_modules/.bin/vite ]; then
  # 优先用项目本地依赖里的 vite：环境里若装了残缺的 pnpm（如全局 shim 损坏），
  # 走 pnpm/npm 分支反而失败；node_modules 就绪时直接用它最稳。
  ./node_modules/.bin/vite build --mode production
elif command -v pnpm >/dev/null 2>&1; then
  pnpm build:prod
else
  npm run build:prod
fi
[ -d dist ] || die "dist 目录未生成"
c_ok "dist 构建完成"

# ---------- 2. 打包构建上下文 ----------
c_info "[2/4] 打包构建上下文 -> $CTX_TAR"
mkdir -p "$STAGE_DIR"
CTX_PATH=$STAGE_DIR/$CTX_TAR
tar -czf "$CTX_PATH" dist Dockerfile nginx.conf docker-entrypoint.sh
ls -lh "$CTX_PATH" | awk '{print "      大小: "$5}'
c_info "    上传到 $BUILD_HOST 并构建 $WEB_IMG"
rssh "$BUILD_HOST" "mkdir -p $BUILD_DIR/frontend $REMOTE_DIR"
rcp "$CTX_PATH" "$BUILD_HOST" "$REMOTE_DIR"

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/build-web.sh" <<'EOS'
set -euo pipefail
CTX_TAR=$1
IMG=$2
NGINX_DEFAULT=$3
D=/data/deploy
B=$D/build/frontend
mkdir -p "$B"
cd "$B"
rm -rf dist Dockerfile nginx.conf docker-entrypoint.sh
tar -xzf "$D/$CTX_TAR"
[ -f "$D/base-images.env" ] && . "$D/base-images.env"
NGINX_BASE=${RESOLVED_NGINX_IMG:-$NGINX_DEFAULT}
echo "  nginx 基础镜像: $NGINX_BASE"
docker build --build-arg NGINX_BASE_IMG="$NGINX_BASE" -t "$IMG" -f Dockerfile .
docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep "$IMG" || true
EOS

rssh "$BUILD_HOST" "bash -s" < "$TMP/build-web.sh" "$CTX_TAR" "$WEB_IMG" "$NGINX_BASE_IMG" \
  && c_ok "镜像构建完成(在 $BUILD_HOST 上)" || die "前端镜像构建失败"

# ---------- 3. 启动容器 ----------
c_info "[3/4] 重启前端容器 bzpt-web($WEB_PORT)"
rssh "$BUILD_HOST" "
set -e
docker rm -f bzpt-web >/dev/null 2>&1 || true
docker run -d --name bzpt-web --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e PROD_API_TARGET=http://127.0.0.1:$GW_PORT/prod-api/ \
  -e UPLOADS_TARGET=http://127.0.0.1:$GW_PORT \
  '$WEB_IMG'
sleep 3
code=\$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$WEB_PORT/ || true)
echo \"前端容器探测: HTTP \$code\"
docker ps --format '{{.Names}}\t{{.Status}}' | grep bzpt-web
"

# ---------- 4. 对外可达性 ----------
c_info "[4/4] 校验对外访问"
code=$(rssh "$BUILD_HOST" "curl -s -m 8 -o /dev/null -w '%{http_code}' http://127.0.0.1:$GW_PORT/" 2>/dev/null || echo 000)
[ "$code" = "200" ] && c_ok "网关 $GW_PORT 返回 200" || c_warn "网关 $GW_PORT 返回 $code(检查 nginx 是否已加载 nginx-gw.conf)"

rssh "$BUILD_HOST" "rm -f $REMOTE_DIR/$CTX_TAR"
c_ok "前端发布完成: http://$GW_PUBLIC/"
