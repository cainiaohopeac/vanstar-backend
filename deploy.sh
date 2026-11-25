#!/bin/bash
# ============================================================
# bzpt-backend 后端 Docker 部署脚本（在本项目根目录运行）
# 用法:
#   bash deploy.sh            # 使用已构建的 ruoyi-admin.jar 部署
#   bash deploy.sh --build    # 强制重新 mvn package 后再部署
# 流程:
#   1) (可选) mvn package 生成 ruoyi-admin.jar
#   2) docker build 构建镜像 bzpt-server:<TAG>
#   3) docker save | gzip 打包
#   4) scp 上传镜像包、deploy-remote.sh 与配置文件模板到 dev 服务器
#   5) 服务器 docker load 并覆盖重启 bzpt-server(host 网络, 8081)
#   6) 清理本地临时压缩包与本地镜像
# 说明: 配置文件(application.yml / application-dev.yml 等)会挂载到宿主机
#       /docker/bzpt-server/config, 首次用本脚本上传的模板初始化, 之后改配置只需
#       docker restart bzpt-server, 无需重新部署
# ============================================================
set -euo pipefail

# ---------- 部署配置 ----------
SERVER=60.217.78.187
TAG=5.X
REMOTE_DIR=/root/bzpt-deploy
IMG=bzpt-server:${TAG}
ARCHIVE=bzpt-server-${TAG}.tar.gz
SSH_OPTS="-o StrictHostKeyChecking=no -o ConnectTimeout=20"

# ---------- 项目路径 ----------
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
JAR=$ROOT/ruoyi-admin/target/ruoyi-admin.jar
# 外置配置模板来源(经 maven 过滤后的运行期配置, 含 application.yml / application-*.yml)
CONF_SRC=$ROOT/ruoyi-admin/target/classes

# ---------- 认证方式 ----------
# 默认走 SSH 免密登录(本机公钥已加入服务器 ~/.ssh/authorized_keys)
# 若需临时改用密码认证: BZPT_SSH_PASS='密码' bash deploy.sh
if [ -n "${BZPT_SSH_PASS:-}" ]; then
  command -v sshpass >/dev/null 2>&1 || { echo "[x] 密码认证需要 sshpass"; exit 1; }
  ssh_run() { sshpass -p "$BZPT_SSH_PASS" ssh $SSH_OPTS root@$SERVER "$@"; }
  scp_put() { sshpass -p "$BZPT_SSH_PASS" scp $SSH_OPTS "$1" root@$SERVER:${REMOTE_DIR}/; }
  # 多文件上传: 第一个参数为目标目录, 其余为本地文件
  # (用 shift 而非负数组下标, 兼容 macOS 自带 bash 3.2)
  scp_put_multi() { local dest="$1"; shift; sshpass -p "$BZPT_SSH_PASS" scp $SSH_OPTS "$@" "root@$SERVER:${dest}"; }
else
  ssh_run() { ssh $SSH_OPTS root@$SERVER "$@"; }
  scp_put() { scp $SSH_OPTS "$1" root@$SERVER:${REMOTE_DIR}/; }
  # 多文件上传: 第一个参数为目标目录, 其余为本地文件
  scp_put_multi() { local dest="$1"; shift; scp $SSH_OPTS "$@" "root@$SERVER:${dest}"; }
fi

# ---------- 0. 前置检查 ----------
command -v docker >/dev/null 2>&1 || { echo "[x] 需要 docker"; exit 1; }
command -v ssh scp >/dev/null 2>&1 || { echo "[x] 需要 ssh / scp"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "[x] 需要 maven"; exit 1; }

# ---------- 1. 构建 jar ----------
if [ "${1:-}" = "--build" ] || [ ! -f "$JAR" ]; then
  echo "[1/6] mvn package 构建后端 jar ..."
  cd "$ROOT"
  mvn -q -pl ruoyi-admin -am package -DskipTests
else
  echo "[1/6] 使用已构建 jar: $JAR"
fi
[ -f "$JAR" ] || { echo "[x] jar 不存在: $JAR"; exit 1; }

# ---------- 2. 构建镜像 ----------
echo "[2/6] 构建镜像 $IMG ..."
docker build -t "$IMG" -f "$ROOT/ruoyi-admin/Dockerfile" "$ROOT/ruoyi-admin"

# ---------- 3. 打包镜像 ----------
ARCHIVE_PATH=/tmp/$ARCHIVE
echo "[3/6] docker save | gzip -> $ARCHIVE_PATH"
docker save "$IMG" | gzip -1 > "$ARCHIVE_PATH"
ls -lh "$ARCHIVE_PATH"

# ---------- 4. 上传 ----------
echo "[4/6] 上传到 $SERVER:$REMOTE_DIR ..."
ssh_run "mkdir -p $REMOTE_DIR/conf-seed"
scp_put "$ARCHIVE_PATH"
scp_put "$ROOT/deploy-remote.sh"
# 上传经 maven 过滤的配置文件模板(仅用于服务器首次初始化, 不会覆盖已存在的外置配置)
shopt -s nullglob
CONF_FILES=("$CONF_SRC"/application*.yml)
shopt -u nullglob
if [ ${#CONF_FILES[@]} -eq 0 ]; then
  echo "[x] 未找到配置文件模板: $CONF_SRC/application*.yml"
  exit 1
fi
scp_put_multi "${REMOTE_DIR}/conf-seed/" "${CONF_FILES[@]}"
echo "    已上传配置模板: ${CONF_FILES[*]##*/}"

# ---------- 5. 远程部署 ----------
echo "[5/6] 服务器 docker load + 覆盖重启容器 ..."
ssh_run "cd $REMOTE_DIR && bash deploy-remote.sh $ARCHIVE"

# 清理本地临时压缩包与本地镜像(仅在部署成功后执行)
rm -f "$ARCHIVE_PATH"
echo "[6/6] 清理本地镜像 $IMG ..."
docker rmi "$IMG" >/dev/null 2>&1 && echo "    已删除本地镜像 $IMG" \
  || echo "    [!] 本地镜像 $IMG 删除失败(可能被本地容器占用), 可手动执行: docker rmi $IMG"

echo ""
echo "[✓] 后端部署完成"
