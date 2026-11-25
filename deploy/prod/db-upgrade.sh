#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

echo "== 条件补加 gaps_json（缺列才加） =="
rcp ./db-upgrade-gaps.sql "$DATA_IP" /tmp
rssh "$DATA_IP" 'C=$(docker exec bzpt-mysql mysql -uroot -p'"$MYSQL_ROOT_PWD"' -N -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=\"'"$MYSQL_DB"'\" AND table_name=\"label_object_track\" AND column_name=\"gaps_json\";" 2>/dev/null); if [ "$C" = "0" ]; then docker exec -i bzpt-mysql mysql -uroot -p'"$MYSQL_ROOT_PWD"' < /tmp/db-upgrade-gaps.sql && echo "gaps_json: 已补加"; else echo "gaps_json: 已存在，跳过"; fi; rm -f /tmp/db-upgrade-gaps.sql'

echo "== 复核 =="
rssh "$DATA_IP" "docker exec bzpt-mysql mysql -uroot -p'$MYSQL_ROOT_PWD' -N -e 'USE $MYSQL_DB; SHOW TABLES LIKE \"label_global_attribute\"; SELECT CONCAT(\"gaps_json: \", COUNT(*)) FROM information_schema.columns WHERE table_schema=\"$MYSQL_DB\" AND table_name=\"label_object_track\" AND column_name=\"gaps_json\";'"
