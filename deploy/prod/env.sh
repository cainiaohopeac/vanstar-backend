#!/bin/bash
# ============================================================
# 生产环境部署 - 公共配置(被 deploy/prod 下所有脚本 source)
# 集群角色:
#   192.168.0.171  接入层   nginx(源码安装, 对外 8088) + 前端容器 bzpt-web(内部 8089)  [唯一公网 60.217.79.104]
#   192.168.0.32   应用层   bzpt-server 8081 / 8082
#   192.168.0.211  应用层   bzpt-server 8081 / 8082
#   192.168.0.223  数据层   MySQL(3306) + Redis(6379) + OCR 服务(8866)   [单机]
#   192.168.0.162  存储层   MinIO(9000/9001) 2TB                        [单机] (已回收, MinIO 由云 OSS 替代, OCR 已迁至 223)
#   192.168.0.50   镜像源   mysql / redis / minio 镜像从这里导出
# ============================================================

# ---------- 服务器 ----------
GW_PUBLIC=60.217.79.104      # 接入层公网 IP(唯一公网出口, 50Mbps)
GW_IP=192.168.0.171          # 接入层内网 IP
APP1_IP=192.168.0.32         # 应用层 1
APP2_IP=192.168.0.211        # 应用层 2
DATA_IP=192.168.0.223        # 数据层 MySQL + Redis
OSS_IP=192.168.0.162         # 存储层 MinIO
SRC_IP=192.168.0.50          # 基础镜像来源机(不部署业务)

# 发布目标可用环境变量覆盖, 便于只发某一台: APP_HOSTS=192.168.0.32 bash 30-deploy-backend.sh
APP_HOSTS=${APP_HOSTS:-"$APP1_IP $APP2_IP"}
ALL_HOSTS="$GW_IP $APP1_IP $APP2_IP $DATA_IP $OSS_IP"

# ---------- SSH ----------
SSH_USER=root
# 建议先执行 00-setup-ssh.sh 配置免密; 未配置时用: BZPT_SSH_PASS='密码' bash xx.sh
SSH_PASS=${BZPT_SSH_PASS:-}
SSH_OPT="-o StrictHostKeyChecking=no -o ConnectTimeout=15 -o ServerAliveInterval=30"

# 内网机(无公网)统一经接入层 171 中转。
# 注意: 171 的 sshd 设置了 AllowTcpForwarding no, ssh -J(ProxyJump) 不可用,
#       因此采用"两段式": ssh 171 "ssh <内网机> <命令>", 命令用 base64 传递避免多层引号转义。
_auth() { if [ -n "$SSH_PASS" ]; then sshpass -p "$SSH_PASS" "$@"; else "$@"; fi; }
_is_gw() { [ "$1" = "$GW_IP" ] || [ "$1" = "$GW_PUBLIC" ]; }

# rssh <host> <command>      远程执行命令(支持 stdin 透传, 如 rssh h "bash -s" < script)
rssh() {
  local h="$1"; shift
  local cmd="$*"
  if _is_gw "$h"; then
    _auth ssh $SSH_OPT "$SSH_USER@$GW_PUBLIC" "$cmd"
  else
    local b64; b64=$(printf '%s' "$cmd" | base64 | tr -d '\n')
    # 用 bash -c "$(...)" 而非管道喂给 bash, 以保证外层 stdin 能透传给远程命令(如 bash -s < script)
    _auth ssh $SSH_OPT "$SSH_USER@$GW_PUBLIC" \
      "ssh $SSH_OPT $SSH_USER@$h 'bash -c \"\$(echo $b64 | base64 -d)\"'"
  fi
}
# rcp <本地文件> <目标机> [远程目录]   上传文件(流式经 171 中转, 不在 171 落盘)
rcp() {
  local src="$1" h="$2" dst="${3:-$REMOTE_DIR}"
  local name; name=$(basename "$src")
  if _is_gw "$h"; then
    _auth scp $SSH_OPT "$src" "$SSH_USER@$GW_PUBLIC:$dst"
  else
    _auth ssh $SSH_OPT "$SSH_USER@$GW_PUBLIC" \
      "ssh $SSH_OPT $SSH_USER@$h 'cat > $dst/$name'" < "$src"
  fi
}

# ---------- 本地离线包 ----------
DOCKER_TGZ=${DOCKER_TGZ:-$HOME/Downloads/docker-29.7.2.tgz}
NGINX_TGZ=${NGINX_TGZ:-$HOME/Downloads/nginx-1.30.4.tar.gz}
PROD_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$PROD_DIR/../.." && pwd)              # 仓库根目录
FRONTEND_DIR=${FRONTEND_DIR:-$(cd "$ROOT/../bz-frontend" && pwd)}

# ---------- 远程目录 ----------
REMOTE_DIR=/data/deploy                          # 上传目录
DATA_ROOT=/data                                  # 数据盘挂载点

# ---------- 镜像 / 容器 ----------
SERVER_IMG=bzpt-server:5.X                       # 后端镜像
WEB_IMG=bzpt-web:5.X                             # 前端镜像
SERVER_PORTS="8081 8082"                         # 每台应用机的实例端口
GW_PORT=8088                                     # 网关对外端口(外网访问入口)
WEB_PORT=8089                                    # 前端容器内部端口(仅接入层, 由网关反代)

# ---------- 构建机(本地不依赖 docker) ----------
# 本地只负责: mvn/vite 产出 jar、dist -> 打包 tar.gz -> 上传 BUILD_HOST
# 镜像构建与分发全部在接入层 171 上完成(它有公网可拉基础镜像, 且与内网机互通)
BUILD_HOST=${BUILD_HOST:-$GW_IP}                 # 镜像构建机
BUILD_DIR=$REMOTE_DIR/build                      # 171 上的构建上下文目录
STAGE_DIR=${STAGE_DIR:-/tmp/bzpt-stage}          # 本地打包暂存目录
BASE_ENV_FILE=$REMOTE_DIR/base-images.env        # 171 上记录已解析成功的基础镜像

# ---------- 业务镜像的基础镜像(本地拉取 -> 上传 171 载入, 不在服务器上 pull) ----------
# 后端 JDK17: 原 bzpt-java17(liberica) 已弃用, 改用 Eclipse Temurin(Adoptium 官方) JRE17,
#             由 11-prepare-base-images.sh 在本地拉取后上传 171 并 docker load
#   需要完整 JDK(在线诊断 jstack/jmap)时改用: eclipse-temurin:17-jdk-jammy
JAVA_BASE_IMG=${JAVA_BASE_IMG:-bzpt-java17:17.0.11-cds}          # 171 上的别名(Dockerfile 默认 FROM)
JAVA_PULL_IMG=${JAVA_PULL_IMG:-eclipse-temurin:17-jre-jammy}     # 实际拉取的镜像
# 前端 nginx: 171 上已有 nginx 镜像, 通常不需要再导入(缺少时才跑 11-prepare-base-images.sh nginx)
NGINX_BASE_IMG=${NGINX_BASE_IMG:-nginx:stable-alpine}
NGINX_PULL_IMG=${NGINX_PULL_IMG:-nginx:stable-alpine}
# registry: 默认走国内镜像站(本机直连 docker.io 不通), 可按需改为 registry-1.docker.io 或其他镜像站
REGISTRY=${REGISTRY:-docker.m.daocloud.io}
PULL_TOOL=$PROD_DIR/tools/pull-base-image.mjs                    # 不依赖 docker CLI 的拉取工具

# 基础镜像(从 192.168.0.50 导出, 实际 tag 由 10-fetch-base-images.sh list 探得)
MYSQL_IMG=${MYSQL_IMG:-image.onecode.cmict.cloud/dzzz/mysql:8.0.18}
REDIS_IMG=${REDIS_IMG:-image.onecode.cmict.cloud/library/redis:8.2.0}
MINIO_IMG=${MINIO_IMG:-image.onecode.cmict.cloud/bigmodel-yfzx/minio:latest}

# ---------- 组件参数 ----------
# 注意: 避免使用 # 等特殊字符, 防止写入 yml 时被解析为注释
MYSQL_ROOT_PWD=${MYSQL_ROOT_PWD:-'BzYufu_2609_Db'}
MYSQL_DB=ry-vue
REDIS_PWD=${REDIS_PWD:-'BzYufu_2609_Rd'}
MINIO_USER=${MINIO_USER:-admin}
MINIO_PWD=${MINIO_PWD:-'REDACTED_MINIO_SECRET_KEY'}
MINIO_BUCKET=mark-server
# 浏览器直连 MinIO 的跨域白名单：不配的话浏览器会拦掉每一条预签名直链
# （表现为标注页点云全部回退到批量接口，加载慢十倍以上；node/curl 测不出来）
MINIO_CORS_ORIGIN=${MINIO_CORS_ORIGIN:-"http://$GW_PUBLIC:$GW_PORT,http://$GW_IP:$GW_PORT"}

JAVA_OPTS=${JAVA_OPTS:-"-Xms1g -Xmx4g -XX:+UseZGC -XX:+HeapDumpOnOutOfMemoryError"}

# ---------- 备份 ----------
BACKUP_DIR=/data/backup        # 备份根目录(MySQL 与 MinIO 各存本机一份并互备到 162 的 2TB 盘)
KEEP_DAYS=${KEEP_DAYS:-7}      # 备份保留天数
CRON_TIME=${CRON_TIME:-"0 2 * * *"}   # 每日备份时间(默认 02:00)

# ---------- 日志 ----------
c_info() { echo -e "\033[36m[INFO]\033[0m $*"; }
c_ok()   { echo -e "\033[32m[OK]\033[0m   $*"; }
c_warn() { echo -e "\033[33m[WARN]\033[0m $*"; }
c_err()  { echo -e "\033[31m[ERR]\033[0m  $*" >&2; }
die()    { c_err "$*"; exit 1; }

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "缺少命令: $1"; }
