#!/bin/bash
# ============================================================
# 步骤 2: 接入层(192.168.0.171) 源码编译安装 Nginx + 部署网关配置
# 用法: bash 02-install-nginx.sh [--reconf]
#   --reconf  已安装时只替换网关配置并 reload
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

RECONF=0
[ "${1:-}" = "--reconf" ] && RECONF=1
[ -f "$NGINX_TGZ" ] || die "源码包不存在: $NGINX_TGZ"
[ -f "$PROD_DIR/conf/nginx-gw.conf" ] || die "网关配置不存在: $PROD_DIR/conf/nginx-gw.conf"

NGINX_VER=$(basename "$NGINX_TGZ" .tar.gz)
PREFIX=/usr/local/nginx

c_info "上传源码包与站点配置到 $GW_IP"
rssh "$GW_IP" "mkdir -p $REMOTE_DIR $PREFIX/conf/conf.d $PREFIX/conf/stream.d"
rcp "$NGINX_TGZ" "$GW_IP" "$REMOTE_DIR"
rcp "$PROD_DIR/conf/nginx-gw.conf" "$GW_IP" "$PREFIX/conf/conf.d"
# 七层网关之外的四层转发: 把内网 MySQL / MinIO 映射到公网
rcp "$PROD_DIR/conf/stream-db-oss.conf" "$GW_IP" "$PREFIX/conf/stream.d"
# 主配置需存在 stream 块(与 http 同级), include 路径相对 conf 目录
# include 相对路径基准是 conf 目录, 用绝对路径避免解析到 conf/conf/stream.d
rssh "$GW_IP" "grep -q '^stream {' $PREFIX/conf/nginx.conf || printf 'stream {\n    include '"$PREFIX"'/conf/stream.d/*.conf;\n}\n' >> $PREFIX/conf/nginx.conf"

if [ "$RECONF" = "1" ]; then
  rssh "$GW_IP" "$PREFIX/sbin/nginx -t && systemctl reload nginx"
  c_ok "网关配置已更新并 reload"
  exit 0
fi

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/install-nginx.sh" <<EOS
set -euo pipefail
D=$REMOTE_DIR
VER=$NGINX_VER
PREFIX=$PREFIX
command -v nginx >/dev/null 2>&1 && { echo "nginx 已安装"; exit 0; }

echo "-- 安装编译依赖 --"
dnf install -y gcc gcc-c++ make pcre-devel zlib-devel openssl-devel >/dev/null 2>&1 \\
  || yum install -y gcc gcc-c++ make pcre-devel zlib-devel openssl-devel >/dev/null 2>&1

echo "-- 编译安装 --"
cd /usr/local/src
rm -rf "\$VER"
tar -xzf "\$D/\$VER.tar.gz" -C /usr/local/src
cd "\$VER"
./configure --prefix=\$PREFIX \\
  --with-http_ssl_module \\
  --with-http_v2_module \\
  --with-http_stub_status_module \\
  --with-http_realip_module \\
  --with-http_gzip_static_module \\
  --with-stream >/dev/null
make -j"\$(nproc)" >/dev/null
make install >/dev/null

echo "-- 主配置引入 conf.d --"
# 注意: nginx include 的相对路径基于 conf 目录, 这里用绝对路径避免解析到 conf/conf/conf.d
grep -q "conf.d" \$PREFIX/conf/nginx.conf \\
  || sed -i 's#^http {#http {\n    include '"\$PREFIX"'/conf/conf.d/*.conf;#' \$PREFIX/conf/nginx.conf

echo "-- systemd 单元 --"
cat > /etc/systemd/system/nginx.service <<'EOF'
[Unit]
Description=nginx - high performance web server
After=network-online.target
Wants=network-online.target
[Service]
Type=forking
ExecStart=/usr/local/nginx/sbin/nginx
ExecReload=/usr/local/nginx/sbin/nginx -s reload
ExecStop=/usr/local/nginx/sbin/nginx -s quit
PrivateTmp=true
Restart=always
[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
\$PREFIX/sbin/nginx -t
systemctl enable --now nginx
\$PREFIX/sbin/nginx -v
EOS

c_info "编译安装 nginx(约 1-3 分钟)"
rssh "$GW_IP" "bash -s" < "$TMP/install-nginx.sh"
c_ok "nginx 安装完成: http://$GW_PUBLIC/"
c_warn "安全组需放行 80/443 公网入站, 否则外网无法访问"
