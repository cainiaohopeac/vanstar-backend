#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh
# 在应用层容器内确认新代码特征：global-attributes 接口类与 gaps_json mapper
rssh "$APP1_IP" "docker ps --format '{{.Names}}' | grep -i server | head -3"
rssh "$APP1_IP" "docker exec bzpt-server-8081 sh -c 'ls /app 2>/dev/null || ls / | head -20'"
