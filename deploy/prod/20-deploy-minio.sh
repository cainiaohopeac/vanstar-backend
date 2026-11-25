#!/bin/bash
# ============================================================
# 步骤 4: 部署 MinIO -> 192.168.0.162(2TB 数据盘, 单机 4 目录)
# 用法: bash 20-deploy-minio.sh
# 前置: 已执行 01-install-docker.sh 与 10-fetch-base-images.sh sync
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

c_info "[1/3] 准备数据目录 $OSS_IP:/data/minio1..4"
rssh "$OSS_IP" "mkdir -p /data/minio1 /data/minio2 /data/minio3 /data/minio4"

c_info "[2/3] 启动容器 bzpt-minio(9000 API / 9001 控制台)"
rssh "$OSS_IP" "
set -e
docker rm -f bzpt-minio >/dev/null 2>&1 || true
docker run -d --name bzpt-minio --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e MINIO_ROOT_USER='$MINIO_USER' \
  -e MINIO_ROOT_PASSWORD='$MINIO_PWD' \
  -e MINIO_API_CORS_ALLOW_ORIGIN="${MINIO_CORS_ORIGIN}" \
  -v /data/minio1:/data1 \
  -v /data/minio2:/data2 \
  -v /data/minio3:/data3 \
  -v /data/minio4:/data4 \
  '$MINIO_IMG' server /data{1...4} --console-address ':9001'
"

c_info "[3/3] 健康检查(最长 60s)"
rssh "$OSS_IP" "
for i in \$(seq 1 20); do
  code=\$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9000/minio/health/live || true)
  [ \"\$code\" = '200' ] && { echo 'MinIO 就绪'; break; }
  sleep 3
done
docker ps --format '{{.Names}}\t{{.Status}}' | grep bzpt-minio || { echo '容器未运行'; exit 1; }
"

c_ok "MinIO 部署完成: http://$OSS_IP:9000 (控制台 9001)"
c_warn "1) 安全组需放行内网段访问 9000/9001(应用机 32/211 要连)"
c_warn "2) 桶 $MINIO_BUCKET 由应用 initialize-bucket 自动创建, 也可登录控制台手工建"
c_warn "3) 记得把 $OSS_IP:9000 与凭据写入外置配置 application-dev.yml 的 minio 段"
