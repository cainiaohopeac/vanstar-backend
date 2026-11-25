#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh
rssh "$DATA_IP" "docker exec bzpt-mysql mysql -uroot -p'$MYSQL_ROOT_PWD' -N -e 'USE $MYSQL_DB; SELECT CONCAT(\"table: \", table_name) FROM information_schema.tables WHERE table_schema=\"$MYSQL_DB\" AND table_name IN (\"label_object_track\",\"label_global_attribute\"); SELECT CONCAT(\"gaps_json: \", count(*)) FROM information_schema.columns WHERE table_schema=\"$MYSQL_DB\" AND table_name=\"label_object_track\" AND column_name=\"gaps_json\";'"
