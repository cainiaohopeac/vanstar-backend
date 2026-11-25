#!/bin/bash
# ============================================================
# 测试环境部署 - 公共配置(被 deploy/test 下所有脚本 source)
# 与生产(deploy/prod)同思路: 本机只做触发, 拉码/Maven/Node 构建/docker build
# 全部在测试机 187 上用容器完成; 本机不需要 JDK / Maven / Node / docker。
# 区别: 测试环境是单机(前后端都在 187), 无需跳板机, ssh 直连。
# 依赖镜像(JDK17 / nginx / maven / node / git)不从外网拉取,
# 由 10-prepare-base-images.sh 从生产构建机 171 迁移。
#
# 拓扑:
#   60.217.78.187  测试机  bzpt-server(8081, host 网络) + bzpt-web(8088, host 网络)
#                  MySQL / Redis 本机容器; OCR 在生产 162
#   60.217.79.104  生产构建机 171  依赖镜像来源 + Deploy Key 来源
# ============================================================

# ---------- 服务器 ----------
TEST_HOST=${TEST_HOST:-60.217.78.187}    # 测试机(构建 + 运行, 单机)
PROD_BUILD_HOST=${PROD_BUILD_HOST:-60.217.79.104}   # 生产构建机 171(只读: 迁移镜像/密钥)

# ---------- SSH ----------
SSH_USER=root
SSH_PASS=${BZPT_SSH_PASS:-}
SSH_OPT="-o StrictHostKeyChecking=no -o ConnectTimeout=20 -o ServerAliveInterval=30"
_auth() { if [ -n "$SSH_PASS" ]; then sshpass -p "$SSH_PASS" "$@"; else "$@"; fi; }
# 测试机直连, 无需跳板
tssh() { _auth ssh $SSH_OPT "$SSH_USER@$TEST_HOST" "$@"; }
tcp()  { _auth scp $SSH_OPT "$1" "$SSH_USER@$TEST_HOST:${2:-$REMOTE_DIR/}"; }

# ---------- 远程目录(187) ----------
REMOTE_DIR=/root/bzpt-deploy
CI_DIR=$REMOTE_DIR/ci                            # ci 脚本 + deploy_key
SRC_DIR=$REMOTE_DIR/src                          # 代码检出(持久, 增量 fetch)
BUILD_DIR=$REMOTE_DIR/build                      # 构建中间产物
LOG_DIR=$REMOTE_DIR/logs                         # 发布日志归档
BASE_ENV_FILE=$REMOTE_DIR/base-images.env        # 187 上记录已迁移的基础镜像

# ---------- 镜像 / 容器 ----------
SERVER_IMG=bzpt-server:5.X                       # 后端镜像(与生产同名不同机)
WEB_IMG=bzpt-web:5.X                             # 前端镜像
SERVER_PORT=8081                                 # 后端端口(单实例)
SNAIL_PORT=28080                                 # snail-job 端口
WEB_PORT=8088                                    # 前端 nginx 端口(测试对外入口)

# ---------- 宿主机目录(187, 与现有部署一致) ----------
HOST_CONF_DIR=/docker/bzpt-server/config         # 后端外置配置 -> 容器 /ruoyi/server/config
HOST_LOG_DIR=/docker/bzpt-server/logs            # 后端日志 -> 容器 /ruoyi/server/logs

# ---------- 运行参数 ----------
JAVA_OPTS=${JAVA_OPTS:-"-Xms256m -Xmx1536m -XX:MaxMetaspaceSize=512m"}
# 前端容器代理目标: 测试无网关, 直接指向本机后端
# 注意单机语义(见 bz-frontend/nginx.conf): proxy_pass 带 URI "/" 会替换掉 /prod-api/ 前缀,
# 即 /prod-api/auth/x -> 后端 /auth/x; 不能写成 :8081/prod-api/(那是集群网关的写法)
PROD_API_TARGET=${PROD_API_TARGET:-"http://127.0.0.1:$SERVER_PORT/"}
UPLOADS_TARGET=${UPLOADS_TARGET:-"http://127.0.0.1:$SERVER_PORT"}

# ---------- 依赖镜像(全部从生产 171 迁移, 187 不联网拉镜像) ----------
# 详见 10-prepare-base-images.sh; JAVA 别名与生产保持一致(Dockerfile 默认 FROM)
JAVA_BASE_ALIAS=${JAVA_BASE_ALIAS:-bzpt-java17:17.0.11-cds}
BASE_IMAGES=${BASE_IMAGES:-"maven:3.9-eclipse-temurin-17 node:22-alpine bzpt-git:1.0 eclipse-temurin:17-jre-jammy nginx:stable-alpine"}

# ---------- 输出 ----------
c_info() { echo -e "\033[36m[INFO]\033[0m $*"; }
c_ok()   { echo -e "\033[32m[OK]\033[0m   $*"; }
c_warn() { echo -e "\033[33m[WARN]\033[0m $*"; }
c_err()  { echo -e "\033[31m[ERR]\033[0m $*" >&2; }
die()    { c_err "$*"; exit 1; }
