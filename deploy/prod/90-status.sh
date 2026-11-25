#!/bin/bash
# ============================================================
# 巡检: 一键查看 5 台机器的容器状态与服务健康
# 用法: bash 90-status.sh
# ============================================================
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

c_info "===== 各机容器 ====="
for h in $GW_IP $APP1_IP $APP2_IP $DATA_IP $OSS_IP; do
  echo "--- $h ---"
  rssh "$h" "docker ps --format '  {{.Names}}\t{{.Status}}\t{{.Ports}}'" 2>/dev/null || echo "  不可达"
done

c_info "===== 后端实例探活 ====="
for h in $APP_HOSTS; do
  for p in $SERVER_PORTS; do
    code=$(rssh "$h" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$p/auth/tenant/list" 2>/dev/null || echo ERR)
    echo "  $h:$p -> HTTP $code"
  done
done

c_info "===== 接入层 ====="
echo "  前端容器 $WEB_PORT -> HTTP $(rssh "$GW_IP" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$WEB_PORT/" 2>/dev/null || echo ERR)"
echo "  网关 $GW_PORT      -> HTTP $(rssh "$GW_IP" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$GW_PORT/" 2>/dev/null || echo ERR)"
echo "  外网入口           -> HTTP $(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://$GW_PUBLIC:$GW_PORT/" || echo ERR)"

c_info "===== 数据层 ====="
echo "  MySQL $DATA_IP:3306 -> $(rssh "$DATA_IP" "docker exec bzpt-mysql mysqladmin ping -uroot -p'$MYSQL_ROOT_PWD' 2>/dev/null | tail -1" 2>/dev/null || echo ERR)"
echo "  Redis $DATA_IP:6379 -> $(rssh "$DATA_IP" "docker exec bzpt-redis redis-cli -a '$REDIS_PWD' ping 2>/dev/null | tail -1" 2>/dev/null || echo ERR)"

c_info "===== 对象存储 ====="
echo "  MinIO $OSS_IP:9000 -> HTTP $(rssh "$OSS_IP" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:9000/minio/health/live" 2>/dev/null || echo ERR)"
