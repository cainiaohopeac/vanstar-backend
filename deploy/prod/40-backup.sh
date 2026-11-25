#!/bin/bash
# ============================================================
# 步骤 8: 备份(MySQL / MinIO / 外置配置)
# 用法:
#   bash 40-backup.sh all                 # 全部备份
#   bash 40-backup.sh mysql               # 备份 MySQL(223), 并同步一份到 162 的 2TB 盘
#   bash 40-backup.sh minio               # MinIO 数据目录硬链接快照(162)
#   bash 40-backup.sh conf                # 外置配置与网关配置
#   bash 40-backup.sh --install-cron      # 在接入层 171 安装每日定时任务
# 说明:
#   - MySQL 用 mysqldump(--single-transaction, 不锁表), 指定 utf8mb4 避免中文乱码
#   - MinIO 镜像内无 mc, 采用同盘硬链接快照(cp -al): 秒级、几乎不占空间;
#     如需对象级备份, 建议后续在控制台创建桶后自行接入 mc mirror
#   - 保留策略: KEEP_DAYS(默认 7 天)
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

TS=$(date +%Y%m%d_%H%M%S)

# ---------- MySQL ----------
backup_mysql() {
  local f="ry-vue_${TS}.sql.gz"
  c_info "[MySQL] 导出 ry-vue -> $DATA_IP:$BACKUP_DIR/mysql/$f"
  rssh "$DATA_IP" "mkdir -p $BACKUP_DIR/mysql"
  rssh "$DATA_IP" "docker exec bzpt-mysql mysqldump -uroot -p'$MYSQL_ROOT_PWD' \
    --default-character-set=utf8mb4 --single-transaction --routines --triggers --events ry-vue \
    2>/dev/null | gzip > $BACKUP_DIR/mysql/$f"
  rssh "$DATA_IP" "ls -lh $BACKUP_DIR/mysql/$f | awk '{print \"      大小: \"\$5}'"

  c_info "[MySQL] 同步备份到 $OSS_IP (2TB 盘)"
  rssh "$GW_IP" "ssh $SSH_OPT $SSH_USER@$OSS_IP 'mkdir -p $BACKUP_DIR/mysql'"
  rssh "$GW_IP" "ssh $SSH_OPT $SSH_USER@$DATA_IP 'cat $BACKUP_DIR/mysql/$f' | ssh $SSH_OPT $SSH_USER@$OSS_IP 'cat > $BACKUP_DIR/mysql/$f'"

  rssh "$DATA_IP" "find $BACKUP_DIR/mysql -name 'ry-vue_*.sql.gz' -mtime +$KEEP_DAYS -delete"
  rssh "$OSS_IP"  "find $BACKUP_DIR/mysql -name 'ry-vue_*.sql.gz' -mtime +$KEEP_DAYS -delete"
  c_ok "MySQL 备份完成"
}

# ---------- MinIO ----------
backup_minio() {
  c_info "[MinIO] 数据目录硬链接快照 -> $OSS_IP:$BACKUP_DIR/minio"
  rssh "$OSS_IP" "mkdir -p $BACKUP_DIR/minio"
  rssh "$OSS_IP" "for i in 1 2 3 4; do rm -rf $BACKUP_DIR/minio/minio\${i}_${TS}; cp -al /data/minio\$i $BACKUP_DIR/minio/minio\${i}_${TS}; done"
  rssh "$OSS_IP" "du -sh $BACKUP_DIR/minio/minio1_${TS} | awk '{print \"      快照: \"\$1}'"
  rssh "$OSS_IP" "find $BACKUP_DIR/minio -maxdepth 1 -name 'minio?_*' -mtime +$KEEP_DAYS -exec rm -rf {} +"
  c_ok "MinIO 备份完成"
}

# ---------- 配置 ----------
backup_conf() {
  c_info "[配置] 备份外置配置与网关配置 -> $GW_IP:$BACKUP_DIR/conf"
  rssh "$GW_IP" "mkdir -p $BACKUP_DIR/conf"
  rssh "$GW_IP" "cp -f /usr/local/nginx/conf/conf.d/nginx-gw.conf $BACKUP_DIR/conf/nginx-gw_${TS}.conf"
  for h in $APP_HOSTS; do
    # 内网机无 tar, 直接用 scp -r 拉取外置配置目录(171 -> 应用机已免密)
    rssh "$GW_IP" "rm -rf $BACKUP_DIR/conf/${h}_${TS}; mkdir -p $BACKUP_DIR/conf/${h}_${TS}"
    rssh "$GW_IP" "scp -r $SSH_OPT $SSH_USER@$h:/data/bzpt/config $BACKUP_DIR/conf/${h}_${TS}/ >/dev/null"
    c_ok "  $h 外置配置已备份"
  done
  rssh "$GW_IP" "find $BACKUP_DIR/conf -mtime +$KEEP_DAYS -type f -delete"
  c_ok "配置备份完成"
}

# ---------- 安装定时任务 ----------
install_cron() {
  c_info "上传脚本到 $GW_IP:$REMOTE_DIR 并安装定时任务($CRON_TIME)"
  rssh "$GW_IP" "mkdir -p $REMOTE_DIR $BACKUP_DIR"
  rcp "$PROD_DIR/40-backup.sh" "$GW_IP" "$REMOTE_DIR"
  rcp "$PROD_DIR/env.sh" "$GW_IP" "$REMOTE_DIR"
  # cronie 可能未安装
  rssh "$GW_IP" "command -v crontab >/dev/null 2>&1 || (dnf install -y cronie >/dev/null 2>&1 || yum install -y cronie >/dev/null 2>&1)"
  rssh "$GW_IP" "systemctl enable --now crond >/dev/null 2>&1 || service crond start >/dev/null 2>&1 || true"
  rssh "$GW_IP" "(crontab -l 2>/dev/null | grep -v '40-backup.sh' || true; echo '$CRON_TIME bash $REMOTE_DIR/40-backup.sh all >> $BACKUP_DIR/backup.log 2>&1') | crontab -"
  rssh "$GW_IP" "crontab -l | grep 40-backup.sh"
  c_ok "定时任务已安装"
}

case "${1:-all}" in
  mysql)  backup_mysql;;
  minio)  backup_minio;;
  conf)   backup_conf;;
  all)    backup_mysql; backup_minio; backup_conf;;
  --install-cron) install_cron;;
  *) die "未知参数: $1 (all|mysql|minio|conf|--install-cron)";;
esac
