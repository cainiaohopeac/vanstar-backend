#!/bin/bash
# ============================================================
# 步骤 0: 配置 SSH 免密(只需执行一次)
#   1) 本地 -> 接入层 171(公网 60.217.79.104)
#   2) 接入层 171 -> 所有内网机(32/211/223/162/50), 作为跳板访问的前提
# 说明: 优先使用 sshpass; 若所在环境没有 sshpass / ssh-copy-id(如 Windows Git Bash,
#       macOS 默认环境), 自动改用 OpenSSH 自带的 SSH_ASKPASS 机制提供密码, 无需额外安装。
#       已经免密的机器会自动跳过, 可重复执行。
# 用法: BZPT_SSH_PASS='服务器密码' bash 00-setup-ssh.sh
# ============================================================
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"

PASS=${BZPT_SSH_PASS:-}
[ -n "$PASS" ] || die "首次配置需要服务器密码: BZPT_SSH_PASS='xxx' bash 00-setup-ssh.sh"

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

HAVE_SSHPASS=0
command -v sshpass >/dev/null 2>&1 && HAVE_SSHPASS=1
[ "$HAVE_SSHPASS" = "1" ] && c_info "认证方式: sshpass" || c_info "认证方式: SSH_ASKPASS(未找到 sshpass)"

# SSH_ASKPASS 回退用的脚本: 收到密码提示时直接输出密码
ASK=$TMP/askpass.sh
cat > "$ASK" <<EOA
#!/bin/sh
echo "$PASS"
EOA
chmod 700 "$ASK"

# 带密码的 ssh: 优先 sshpass, 否则用 SSH_ASKPASS(需 OpenSSH 8.4+)
pass_ssh() {
  local h="$1"; shift
  if [ "$HAVE_SSHPASS" = "1" ]; then
    sshpass -p "$PASS" ssh $SSH_OPT "$SSH_USER@$h" "$@"
  else
    env SSH_ASKPASS="$ASK" SSH_ASKPASS_REQUIRE=force DISPLAY=:0 \
      ssh $SSH_OPT "$SSH_USER@$h" "$@"
  fi
}

# 是否已免密
already_ok() {
  ssh $SSH_OPT -o BatchMode=yes -o ConnectTimeout=8 "$SSH_USER@$1" true >/dev/null 2>&1
}

# 把 pub 公钥写入目标机 authorized_keys
install_pub() {
  local host="$1" pub="$2"
  local rc="mkdir -p ~/.ssh && grep -qxF '$pub' ~/.ssh/authorized_keys 2>/dev/null || echo '$pub' >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys"
  if [ "$HAVE_SSHPASS" = "1" ]; then
    sshpass -p "$PASS" ssh $SSH_OPT "$SSH_USER@$host" "$rc" >/dev/null 2>&1
  else
    env SSH_ASKPASS="$ASK" SSH_ASKPASS_REQUIRE=force DISPLAY=:0 \
      ssh $SSH_OPT "$SSH_USER@$host" "$rc" >/dev/null 2>&1
  fi
}

# ---------- 1. 本地密钥 ----------
c_info "[1/4] 检查本地密钥"
if [ ! -f "$HOME/.ssh/id_rsa" ]; then
  mkdir -p "$HOME/.ssh"
  ssh-keygen -t rsa -b 4096 -N '' -f "$HOME/.ssh/id_rsa" >/dev/null
  c_ok "已生成本地密钥"
fi
LOCAL_PUB=$(cat "$HOME/.ssh/id_rsa.pub")
c_ok "本地密钥就绪: $HOME/.ssh/id_rsa"

# ---------- 2. 本地 -> 接入层 ----------
c_info "[2/4] 分发本地公钥到接入层 $GW_PUBLIC"
if already_ok "$GW_PUBLIC"; then
  c_ok "本地 -> $GW_PUBLIC 已免密, 跳过"
elif install_pub "$GW_PUBLIC" "$LOCAL_PUB"; then
  c_ok "本地 -> $GW_PUBLIC 免密完成"
else
  die "登录 $GW_PUBLIC 失败, 请确认密码与安全组是否放行 22 端口
  若本机 ssh 版本过老不支持 SSH_ASKPASS(需 OpenSSH 8.4+), 可手工执行:
    cat ~/.ssh/id_rsa.pub | ssh root@$GW_PUBLIC 'mkdir -p ~/.ssh; cat >> ~/.ssh/authorized_keys'"
fi

GW_SSH=(ssh $SSH_OPT "$SSH_USER@$GW_PUBLIC")

# ---------- 3. 接入层 -> 各内网机 ----------
c_info "[3/4] 接入层生成密钥并分发到内网机"
"${GW_SSH[@]}" "mkdir -p ~/.ssh; [ -f ~/.ssh/id_rsa ] || ssh-keygen -t rsa -b 4096 -N '' -f ~/.ssh/id_rsa >/dev/null 2>&1"
GW_PUB=$("${GW_SSH[@]}" "cat ~/.ssh/id_rsa.pub")

# 171 同样可能没有 sshpass, 传一个 askpass 脚本上去备用
GW_ASK=$REMOTE_DIR/.ssh-askpass
rssh "$GW_IP" "mkdir -p $REMOTE_DIR; printf '%s\n' '#!/bin/sh' \"echo '$PASS'\" > $GW_ASK; chmod 700 $GW_ASK"

for h in $APP1_IP $APP2_IP $DATA_IP $OSS_IP $SRC_IP; do
  if "${GW_SSH[@]}" "ssh $SSH_OPT -o BatchMode=yes -o ConnectTimeout=8 $SSH_USER@$h true >/dev/null 2>&1"; then
    c_ok "171 -> $h 已免密, 跳过"; continue
  fi
  ok=0
  # 方式 1: 171 上有 sshpass
  "${GW_SSH[@]}" "command -v sshpass >/dev/null 2>&1 && sshpass -p '$PASS' ssh-copy-id -o StrictHostKeyChecking=no $SSH_USER@$h >/dev/null 2>&1" && ok=1
  # 方式 2: ssh + SSH_ASKPASS(需 171 的 OpenSSH >= 8.4)
  if [ "$ok" = "0" ]; then
    "${GW_SSH[@]}" "SSH_ASKPASS=$GW_ASK SSH_ASKPASS_REQUIRE=force DISPLAY=:0 ssh $SSH_OPT $SSH_USER@$h 'mkdir -p ~/.ssh && grep -qxF \"$GW_PUB\" ~/.ssh/authorized_keys 2>/dev/null || echo \"$GW_PUB\" >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys' >/dev/null 2>&1" && ok=1
  fi
  if [ "$ok" = "1" ]; then
    c_ok "171 -> $h 免密完成"
  else
    c_warn "171 -> $h 失败(检查该机是否开机/22 端口是否对内网放行);
           或手工在 171 上执行: ssh-copy-id root@$h"
  fi
done
rssh "$GW_IP" "rm -f $GW_ASK"

# ---------- 4. 验证 ----------
c_info "[4/4] 验证跳板连通性"
for h in $APP1_IP $APP2_IP $DATA_IP $OSS_IP $SRC_IP; do
  rssh "$h" "hostname -I | awk '{print \$1}'" >/dev/null 2>&1 \
    && c_ok "$h 可达" || c_warn "$h 不可达"
done
c_ok "SSH 免密配置完成, 后续脚本无需再带 BZPT_SSH_PASS"
