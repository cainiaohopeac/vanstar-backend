#!/bin/bash
# ============================================================
# 步骤 3: 从 192.168.0.50 导出基础镜像并载入目标机(mysql/redis/minio)
# 用法:
#   bash 10-fetch-base-images.sh list          # 查看 .50 上可用的基础镜像
#   bash 10-fetch-base-images.sh sync all      # 按默认映射同步(mysql/redis->223, minio->162)
#   bash 10-fetch-base-images.sh sync mysql:8.0.42 192.168.0.223
# 说明: 采用 docker save | ssh docker load 流式传输, 不落本地磁盘(经 171 中转)
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

# 全部走 env.sh 的 rssh(内网机经 171 两段式中转, 171 的 sshd 未开放 TCP 转发)
src_ssh() { rssh "$SRC_IP" "$*"; }
dst_ssh() { local h="$1"; shift; rssh "$h" "$*"; }

# 镜像迁移: 在 171 上完成 "从 .50 save -> 目标机 load" 的管道, 数据全程走内网
sync_img() {
  local img="$1" h="$2"
  rssh "$GW_IP" "ssh $SSH_OPT $SSH_USER@$SRC_IP 'docker save $img' | ssh $SSH_OPT $SSH_USER@$h 'docker load'"
}

CMD=${1:-list}

case "$CMD" in
  list)
    c_info "192.168.0.50 上的基础镜像:"
    src_ssh "docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep -Ei 'mysql|redis|minio' || echo '(无匹配)'"
    echo
    c_warn "把实际 tag 填到 env.sh 的 MYSQL_IMG / REDIS_IMG / MINIO_IMG"
    ;;

  sync)
    if [ "${2:-all}" = "all" ]; then
      c_info "同步 $MYSQL_IMG -> $DATA_IP"
      sync_img "$MYSQL_IMG" "$DATA_IP"
      c_info "同步 $REDIS_IMG -> $DATA_IP"
      sync_img "$REDIS_IMG" "$DATA_IP"
      c_info "同步 $MINIO_IMG -> $OSS_IP"
      sync_img "$MINIO_IMG" "$OSS_IP"
    else
      IMG="$2"; TARGET="$3"
      [ -n "$IMG" ] && [ -n "${TARGET:-}" ] || die "用法: bash 10-fetch-base-images.sh sync <镜像:tag> <目标机IP>"
      c_info "同步 $IMG -> $TARGET"
      sync_img "$IMG" "$TARGET"
    fi
    c_ok "基础镜像同步完成"
    c_info "223 镜像:"; dst_ssh "$DATA_IP" "docker images --format '{{.Repository}}:{{.Tag}}' | grep -Ei 'mysql|redis'"
    c_info "162 镜像:"; dst_ssh "$OSS_IP"  "docker images --format '{{.Repository}}:{{.Tag}}' | grep -i minio"
    ;;

  *) die "未知子命令: $CMD (list|sync)";;
esac
