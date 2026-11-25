#!/bin/bash
# ============================================================
# bzpt-backend 服务器端部署脚本（在 dev 服务器 60.217.78.187 上运行）
# 由本地 deploy.sh 上传后调用: bash deploy-remote.sh <镜像包.tar.gz>
#   - docker load 镜像
#   - 外置配置文件挂载(首次用 conf-seed 模板初始化, 之后以服务器文件为准)
#   - 覆盖重启 bzpt-server 容器(host 网络, 8081/28080)
#   - 健康检查
# ============================================================
set -euo pipefail

ARCHIVE=${1:-bzpt-server-5.X.tar.gz}
IMG=bzpt-server:5.X
SERVER_PORT=8081      # 后端端口(与现有容器一致)
SNAIL_PORT=28080         # snail-job 端口

# 宿主机目录: 配置与日志持久化, 改配置后仅需 docker restart bzpt-server 即可生效
HOST_DIR=/docker/bzpt-server
CONF_DIR=$HOST_DIR/config

# ---------- 1. 加载镜像 ----------
echo "[1/4] docker load 镜像 $ARCHIVE ..."
if [ -f "$ARCHIVE" ]; then
  docker load -i "$ARCHIVE"
else
  echo "[x] 找不到镜像压缩包: $ARCHIVE"
  exit 1
fi

# ---------- 2. 初始化外置配置(仅首次) + 覆盖重启容器 ----------
echo "[2/4] 初始化外置配置并覆盖重启 bzpt-server ..."
docker rm -f bzpt-server >/dev/null 2>&1 || true

mkdir -p "$HOST_DIR/logs" "$CONF_DIR"
# 首次部署时从随包上传的模板初始化; 之后以宿主机上的文件为准(不会被覆盖)
if [ -d conf-seed ]; then
  for f in conf-seed/*.yml; do
    [ -e "$f" ] || continue
    base=$(basename "$f")
    if [ ! -f "$CONF_DIR/$base" ]; then
      cp "$f" "$CONF_DIR/$base"
      echo "  初始化外置配置: $CONF_DIR/$base"
    else
      echo "  保留已有外置配置: $CONF_DIR/$base"
    fi
  done
fi
# 兼容旧版: 若历史 logs 目录在别处则忽略; 统一持久化日志
docker run -d --name bzpt-server --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e SERVER_PORT=$SERVER_PORT \
  -e SNAIL_PORT=$SNAIL_PORT \
  -e JAVA_OPTS="-Xms256m -Xmx1536m -XX:MaxMetaspaceSize=512m" \
  -v $HOST_DIR/logs:/ruoyi/server/logs \
  -v $CONF_DIR:/ruoyi/server/config \
  "$IMG"

# ---------- 3. 健康检查(等待后端启动, 最长 90s) ----------
echo "[3/4] 健康检查(等待后端启动, 最长 90s)..."
OK=0
for i in $(seq 1 18); do
  code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:${SERVER_PORT}/auth/tenant/list 2>/dev/null || true)
  if [ "$code" = "200" ]; then OK=1; break; fi
  sleep 5
done

# ---------- 4. 结果输出 ----------
echo "[4/4] 部署结果:"
echo "---- 容器状态 ----"
docker ps --filter "name=bzpt-server" --format "table {{.Names}}\t{{.Status}}"
echo "---- 端口监听 ----"
ss -ltn 2>/dev/null | grep -E "(:$SERVER_PORT|:$SNAIL_PORT)" || true
echo "---- 后端接口探测 ----"
if [ "$OK" = "1" ]; then
  curl -s -m 5 -o /dev/null -w "后端 ${SERVER_PORT} /auth/tenant/list -> HTTP %{http_code}\n" http://127.0.0.1:${SERVER_PORT}/auth/tenant/list
else
  echo "后端 ${SERVER_PORT} -> 未在 90s 内就绪, 请查看日志: docker logs -f bzpt-server"
  exit 1
fi
echo "[✓] 后端部署完成"
