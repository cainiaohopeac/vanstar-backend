#!/bin/bash
# ============================================================
# 步骤 1: 各服务器离线安装 Docker(使用 docker-29.7.2.tgz 静态包)
# 用法:
#   bash 01-install-docker.sh all          # 全部 5 台业务机
#   bash 01-install-docker.sh 192.168.0.32 # 指定机器
#   bash 01-install-docker.sh all --mkfs   # 数据盘未格式化时一并格式化(高危)
# 说明: 数据盘默认挂载到 /data, 已格式化则只挂载; 未格式化需加 --mkfs
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

TARGET=${1:-all}
MKFS=0
[ "${2:-}" = "--mkfs" ] && MKFS=1
[ -f "$DOCKER_TGZ" ] || die "离线包不存在: $DOCKER_TGZ"

case "$TARGET" in
  all)   HOSTS="$ALL_HOSTS";;
  app)   HOSTS="$APP_HOSTS";;
  *)     HOSTS="$TARGET";;
esac

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/install-docker.sh" <<'EOS'
set -euo pipefail
MKFS=$1
D=/data/deploy
command -v docker >/dev/null 2>&1 && { echo "SKIP: docker 已安装: $(docker -v)"; exit 0; }

echo "-- 解压 docker 静态包 --"
mkdir -p "$D"
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
rm -rf /tmp/docker
# 部分云镜像未装 tar, 回退用 python3 解压
if command -v tar >/dev/null 2>&1; then
  tar -xzf "$D"/docker-*.tgz -C /tmp
elif command -v python3 >/dev/null 2>&1; then
  python3 -c 'import tarfile,sys; tarfile.open(sys.argv[1]).extractall("/tmp")' "$D"/docker-*.tgz
else
  echo "FATAL: 需要 tar 或 python3"; exit 1
fi
# 安装到 /usr/bin: 这些机器非交互 shell 的 PATH 不含 /usr/local/bin
install -m 0755 /tmp/docker/* /usr/bin/
rm -rf /tmp/docker

echo "-- 挂载数据盘到 /data --"
if ! findmnt -n /data >/dev/null 2>&1; then
  DEV=$(lsblk -dn -o NAME,SIZE | awk 'NR==1{print "/dev/"$1}' | head -1)
  for d in /dev/vdb /dev/sdb /dev/xvdb; do [ -b "$d" ] && DEV=$d && break; done
  if [ -b "$DEV" ]; then
    mkdir -p /data
    if [ "$MKFS" = "1" ] && ! blkid "$DEV" >/dev/null 2>&1; then
      mkfs.xfs -f "$DEV"
    fi
    if blkid "$DEV" >/dev/null 2>&1; then
      mount "$DEV" /data
      grep -q "^$DEV" /etc/fstab || echo "$DEV /data xfs defaults 0 0" >> /etc/fstab
      echo "数据盘已挂载: $DEV -> /data"
    else
      echo "WARN: $DEV 未格式化, 跳过挂载(加 --mkfs 可格式化)"
    fi
  else
    echo "WARN: 未找到数据盘, 使用系统盘 /data"
  fi
fi
mkdir -p /data/docker /data/deploy

echo "-- 写 systemd 单元 --"
if [ -x /usr/bin/containerd ]; then
cat > /etc/systemd/system/containerd.service <<'EOF'
[Unit]
Description=containerd container runtime
After=network.target
[Service]
ExecStart=/usr/bin/containerd
Restart=always
RestartSec=2
LimitNOFILE=1048576
[Install]
WantedBy=multi-user.target
EOF
fi

cat > /etc/systemd/system/docker.service <<'EOF'
[Unit]
Description=Docker Application Container Engine
After=network-online.target containerd.service
Wants=network-online.target
[Service]
Type=notify
ExecStart=/usr/bin/dockerd
ExecReload=/bin/kill -s HUP $MAINPID
LimitNOFILE=1048576
Restart=always
RestartSec=2
[Install]
WantedBy=multi-user.target
EOF

echo "-- 写 daemon.json --"
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "data-root": "/data/docker",
  "log-driver": "json-file",
  "log-opts": { "max-size": "100m", "max-file": "3" },
  "live-restore": true
}
EOF

systemctl daemon-reload
systemctl enable --now containerd >/dev/null 2>&1 || true
systemctl enable --now docker
sleep 3
docker -v
docker info --format 'data-root={{.DockerRootDir}} status={{.ServerVersion}}'
EOS

for h in $HOSTS; do
  c_info "==== 安装 Docker -> $h ===="
  rssh "$h" "mkdir -p $REMOTE_DIR"
  rcp "$DOCKER_TGZ" "$h" "$REMOTE_DIR"
  rssh "$h" "bash -s" < "$TMP/install-docker.sh" "$MKFS" \
    && c_ok "$h docker 就绪" || c_err "$h docker 安装失败"
done
