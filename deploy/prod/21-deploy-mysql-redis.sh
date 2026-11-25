#!/bin/bash
# ============================================================
# 步骤 5: 部署 MySQL + Redis -> 192.168.0.223(单机)
# 用法:
#   bash 21-deploy-mysql-redis.sh              # 只启动容器(库已存在时)
#   bash 21-deploy-mysql-redis.sh --with-sql   # 同时上传并按序导入 script/sql 脚本
# 前置: 已执行 01-install-docker.sh 与 10-fetch-base-images.sh sync
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

WITH_SQL=0
[ "${1:-}" = "--with-sql" ] && WITH_SQL=1

c_info "[1/4] 准备数据目录"
rssh "$DATA_IP" "mkdir -p /data/mysql /data/redis /data/deploy/sql"

c_info "[2/4] 启动 MySQL(3306)"
rssh "$DATA_IP" "
set -e
docker rm -f bzpt-mysql >/dev/null 2>&1 || true
docker run -d --name bzpt-mysql --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -e MYSQL_ROOT_PASSWORD='$MYSQL_ROOT_PWD' \
  -e MYSQL_DATABASE='$MYSQL_DB' \
  -v /data/mysql:/var/lib/mysql \
  '$MYSQL_IMG' \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_general_ci \
  --default-time-zone=+8:00 \
  --max_connections=500 \
  --innodb_buffer_pool_size=6G \
  --skip-character-set-client-handshake
echo '等待 MySQL 就绪(最长 120s)...'
for i in \$(seq 1 40); do
  docker exec bzpt-mysql mysqladmin ping -uroot -p'$MYSQL_ROOT_PWD' >/dev/null 2>&1 && { echo 'MySQL 就绪'; break; }
  sleep 3
done
docker exec bzpt-mysql mysql -uroot -p'$MYSQL_ROOT_PWD' -e \"CREATE DATABASE IF NOT EXISTS \\\`$MYSQL_DB\\\` DEFAULT CHARACTER SET utf8mb4;\"
docker exec bzpt-mysql mysql -uroot -p'$MYSQL_ROOT_PWD' -e 'show databases;'
"

c_info "[3/4] 启动 Redis(6379, AOF)"
rssh "$DATA_IP" "
set -e
docker rm -f bzpt-redis >/dev/null 2>&1 || true
docker run -d --name bzpt-redis --network host --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -v /data/redis:/data \
  '$REDIS_IMG' \
  redis-server --requirepass '$REDIS_PWD' --appendonly yes --maxmemory 4gb --maxmemory-policy noeviction
sleep 3
docker exec bzpt-redis redis-cli -a '$REDIS_PWD' ping 2>/dev/null | grep -q PONG && echo 'Redis 就绪' || echo 'Redis 未就绪, 请检查日志'
"

if [ "$WITH_SQL" = "1" ]; then
  c_info "[4/4] 上传并按序导入 SQL(主库脚本 -> label 增量脚本 -> update 脚本)"
  rssh "$DATA_IP" "rm -rf /data/deploy/sql && mkdir -p /data/deploy/sql"

  # 按固定顺序收集: 主库 -> label 增量 -> update 升级脚本
  FILES=()
  [ -f "$ROOT/script/sql/ry_vue_5.X.sql" ] && FILES+=("$ROOT/script/sql/ry_vue_5.X.sql")
  while IFS= read -r f; do [ -n "$f" ] && FILES+=("$f"); done < <(ls "$ROOT"/script/sql/label_*.sql 2>/dev/null | sort)
  while IFS= read -r f; do [ -n "$f" ] && FILES+=("$f"); done < <(ls "$ROOT"/script/sql/update/*.sql 2>/dev/null | sort)
  [ ${#FILES[@]} -eq 0 ] && die "未找到脚本: $ROOT/script/sql"

  # 重命名为带序号的名字后上传到 sql 目录(rcp 第三个参数为目录)
  SQL_TMP=$(mktemp -d); trap 'rm -rf "$SQL_TMP"' EXIT
  i=0
  for f in "${FILES[@]}"; do
    i=$((i+1))
    cp "$f" "$SQL_TMP/$(printf '%03d' "$i")_$(basename "$f")"
  done
  c_info "  共 $i 个脚本, 开始上传"
  for f in "$SQL_TMP"/*.sql; do
    rcp "$f" "$DATA_IP" "/data/deploy/sql"
  done
  rssh "$DATA_IP" "
for f in \$(ls /data/deploy/sql/*.sql | sort); do
  echo \"  导入 \$(basename \$f)\"
  # 必须显式指定 utf8mb4: 该镜像默认连接字符集为 latin1, 不指定会把中文存成乱码
  docker exec -i bzpt-mysql mysql -uroot -p'$MYSQL_ROOT_PWD' --default-character-set=utf8mb4 --force '$MYSQL_DB' < \"\$f\" >/dev/null 2>&1 \
    && echo '    OK' || echo '    WARN: 部分语句失败(重复执行或已存在时可忽略)'
done
docker exec bzpt-mysql mysql -uroot -p'$MYSQL_ROOT_PWD' -e 'use $MYSQL_DB; show tables;' | head -20
"
else
  c_info "[4/4] 跳过 SQL 导入(加 --with-sql 可执行初始导入)"
fi

c_ok "数据层部署完成: MySQL $DATA_IP:3306 / Redis $DATA_IP:6379"
c_warn "安全组需放行内网段访问 3306 / 6379(应用机 32/211 要连)"
c_warn "把 $DATA_IP:3306 与 $DATA_IP:6379(含密码)写入外置配置 application-dev.yml"
