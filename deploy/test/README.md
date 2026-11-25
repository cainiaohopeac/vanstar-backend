# 测试环境部署（187 · 远端 CI）

与生产（`deploy/prod/`）同一套思路：**本机只做触发**，拉代码 / Maven / Node 构建 / docker build / 重启容器 / 健康检查全部在测试机 **60.217.78.187（187）** 上用容器完成。本机不需要 JDK、Maven、Node、docker。

**依赖镜像复用生产环境的**：187 不联网拉镜像，JDK17 / nginx / maven / node / git 构建与运行镜像由 `10-prepare-base-images.sh` 一次性从生产构建机 171（60.217.79.104）迁移，tag 与生产完全一致。

## 拓扑

```
本机(任意装了 ssh 的电脑)
  └─ ssh 60.217.78.187 (187, 测试机, 单机)
       ├─ bzpt-server  host 网络 8081 (后端, snail 28080)
       ├─ bzpt-web     host 网络 8088 (前端 nginx, /prod-api 直代 127.0.0.1:8081/ 去掉前缀)
       ├─ MySQL / Redis 本机容器
       └─ 构建容器: bzpt-git / maven:3.9-eclipse-temurin-17 / node:22-alpine
依赖镜像来源: 60.217.79.104 (生产构建机 171, docker save -> 本机流式中转 -> 187 load)
```

## 文件清单

| 位置 | 说明 |
|---|---|
| `deploy/test/env.sh` | 测试环境配置：187 地址、目录、镜像名、端口、ssh 封装 |
| `deploy/test/10-prepare-base-images.sh` | **一次性**：从生产 171 迁移依赖镜像到 187 + 复制 Deploy Key + 写 `base-images.env` |
| `deploy/test/ci/ci.env.sh` | 跑在 187 的服务端配置：目录、仓库、缓存卷、`git_run` 封装、发布锁 |
| `deploy/test/ci/build-backend.sh` | 跑在 187：拉码 → Maven 容器打包 → docker build → 重启 `bzpt-server` + 健康检查 |
| `deploy/test/ci/build-frontend.sh` | 跑在 187：拉码 → Node 容器建 dist → docker build → 重启 `bzpt-web` + 校验 |
| `deploy/test/50-ci-deploy.sh` | **本机唯一日常入口**：md5 增量同步 ci 脚本 → 触发 187 构建 → 实时回显 |
| `deploy/test/51-rollback.sh` | 回滚：复用 187 上的历史 commit 镜像，不重新编译 |

## 一次性准备

```bash
cd deploy/test
bash 10-prepare-base-images.sh          # 迁移依赖镜像 + deploy_key(免密已配时)
bash 10-prepare-base-images.sh list     # 只查看 171/187 两端镜像差异
# 未配免密: BZPT_SSH_PASS='密码' bash 10-prepare-base-images.sh
```

迁移内容（全部来自生产 171，tag 一致）：

| 镜像 | 用途 |
|---|---|
| `maven:3.9-eclipse-temurin-17` | 后端 Maven 构建容器 |
| `node:22-alpine` | 前端构建容器（`packageManager` 是 pnpm@11，必须 node 22） |
| `bzpt-git:1.0` | git 拉码容器（187 不装 git） |
| `eclipse-temurin:17-jre-jammy` | 后端运行基础镜像；自动 tag 出别名 `bzpt-java17:17.0.11-cds`（与生产 Dockerfile 默认 FROM 一致） |
| `nginx:stable-alpine` | 前端运行基础镜像 |

Deploy Key 直接复制生产 171 的 `/data/deploy/ci/deploy_key`（该公钥已加到 Gitee 两个仓库的 Deploy Keys，**无需再去 Gitee 配置**）。

> 磁盘提示：187 根盘可用空间较少（约 10G），新增镜像 + Maven 缓存约 3~5G。建议先 `ssh root@60.217.78.187 "docker image prune -f"` 清理悬挂镜像（脚本会自动检测并提醒）。

## 日常发布

```bash
cd deploy/test
bash 50-ci-deploy.sh backend  master-1030          # 后端: 拉 master-1030 最新并发布
bash 50-ci-deploy.sh frontend feature-1030         # 前端: 指定分支
bash 50-ci-deploy.sh all      master-1030          # 前后端一起
bash 50-ci-deploy.sh backend  master-1030 abc1234  # 发布到指定 commit
```

流程与生产一致：`[1/3] 同步脚本` → `[2/3] 触发` → 远端 `[1/5] 检出代码` → `[2/5] Maven 构建` → `[3/5] 构建镜像` → `[4/5] 配置种子` → `[5/5] 重启 + 健康检查`。日志实时回显本机终端，同时归档到 `187:/root/bzpt-deploy/logs/backend-<时间戳>.log`（自动保留最近 20 份）。

- 镜像 tag 固定 `5.X`，同时打 `<短 commit>` 标签用于回滚。
- 依赖缓存卷 `bzpt-test-m2` / `bzpt-test-node-modules` / `bzpt-test-pnpm-store`：实测首次后端（含下载依赖）约 3 分钟，前端约 4 分钟；有缓存后前端 install 约 1.5 分钟。
- 并发保护：`flock` 发布锁（等 900 秒）。
- pnpm 源：187 访问仓库 `.npmrc` 里的华为云源只有 ~20KiB/s，会报 `The operation was aborted due to timeout`。脚本默认用 `--registry=https://registry.npmmirror.com`（`PNPM_REGISTRY` 可覆盖）。

## 只改配置（无需重新构建）

```bash
vim /docker/bzpt-server/config/application-dev.yml   # ssh 登录 187 修改外置配置
docker restart bzpt-server
```

外置配置以服务器文件为准（首次用随码 `ruoyi-admin/target/classes/application*.yml` 种子初始化，之后不被覆盖）。要强制用随码配置覆盖时：

```bash
FORCE_CONF=1 bash 50-ci-deploy.sh backend master-1030   # 慎用
```

## 回滚

```bash
bash 51-rollback.sh list                 # 查看 187 上可回滚的版本
bash 51-rollback.sh backend  f4c3275     # 后端回滚(不重新编译)
bash 51-rollback.sh frontend 884f4cb
```

前提是该 commit 曾被 `50-ci-deploy.sh` 构建过。

## 发布后验证

```bash
# 本机最后一行应为: ===== [测试] 后端发布完成: bzpt-server:5.X @ <短commit> =====
ssh root@60.217.78.187 "docker ps --format '{{.Names}}|{{.Status}}' | grep bzpt"
curl -s -o /dev/null -w '%{http_code}\n' http://60.217.78.187:8088/                    # 前端 200
curl -s -o /dev/null -w '%{http_code}\n' http://60.217.78.187:8088/prod-api/auth/tenant/list   # 代理 200
ssh root@60.217.78.187 "docker logs -f --tail 200 bzpt-server"
```

## 与生产的差异

| 项 | 生产（deploy/prod） | 测试（deploy/test） |
|---|---|---|
| 机器 | 171 网关 + 32/211 应用 + 223 数据 + 162 存储 | 单机 187（构建 + 前后端运行） |
| 访问方式 | 经 171 两段式跳板（`rssh`） | ssh 直连 187 |
| 依赖镜像来源 | 本地拉取/镜像站上传 171 | **从生产 171 直接迁移**，187 不联网拉镜像 |
| 前端代理 | 网关 8088（`PROD_API_TARGET=http://127.0.0.1/prod-api/`，保留前缀） | 直代本机后端（`http://127.0.0.1:8081/`，`/prod-api/` 前缀被 nginx 替换掉） |
| 后端实例 | 每台 2 实例（8081/8082）滚动发布 | 单实例 8081 |
| 配置目录 | `/data/bzpt/config` | `/docker/bzpt-server/config`（沿用 187 现有布局） |
| 构建目录 | `/data/deploy` | `/root/bzpt-deploy` |
| 缓存卷 | `bzpt-m2` 等 | `bzpt-test-*`（与生产隔离，避免互相污染） |

## 实测记录（2026-11-09 首次跑通）

| 步骤 | 结果 |
|---|---|
| `10-prepare-base-images.sh` | 5 个依赖镜像 171→187 迁移成功，`bzpt-java17:17.0.11-cds` 别名已建，Deploy Key 已复制 |
| `50-ci-deploy.sh backend master-1030` | 拉码 `872ab86aa` → Maven 容器打包 → 镜像构建 → `bzpt-server` 8081 健康检查 200 |
| `50-ci-deploy.sh frontend feature-1030` | 拉码 `f2772c7` → Node 容器构建 → 镜像构建 → `bzpt-web` 8088 首页 200、`/prod-api/auth/tenant/list` → `code=200` |
| `51-rollback.sh list` | 正确列出 `bzpt-server:872ab86aa` / `bzpt-web:f2772c7` |

## 排错

| 现象 | 处理 |
|---|---|
| clone 报 `Permission denied (publickey)` | 187 上 `/root/bzpt-deploy/ci/deploy_key` 缺失或权限非 600；重跑 `10-prepare-base-images.sh` |
| 找不到 `maven:...` / `node:22-alpine` 等镜像 | 重跑 `10-prepare-base-images.sh`（该镜像未迁移） |
| 构建报 `no space left on device` | 187 磁盘满：`docker image prune -f`、`docker volume rm bzpt-test-m2`（会重下依赖）、清理旧构建产物 |
| 停在等待发布锁 | 有人在构建；`ssh root@60.217.78.187 "ls -1t /root/bzpt-deploy/logs/ \| head"` 看是谁的日志 |
| 健康检查失败 | `ssh root@60.217.78.187 "docker logs --tail 100 bzpt-server"`；多为外置配置里 MySQL/Redis/OCR 地址问题 |
| 前端代理返回 HTML | `bzpt-web` 的 `/prod-api` 未通到后端：确认 8081 后端在跑、容器环境变量 `PROD_API_TARGET` 指向 `http://127.0.0.1:8081/` |
| 代理返回 `{"code":404,"msg":"请求地址不存在"}` | `PROD_API_TARGET` 误写成 `http://127.0.0.1:8081/prod-api/`（这是集群网关写法），单机要写 `http://127.0.0.1:8081/` |
| pnpm 安装超时 `aborted due to timeout` | 187 到华为云源太慢；确认构建命令带 `--registry=https://registry.npmmirror.com`（脚本默认已加） |
| 前端构建 `ERR_UNKNOWN_BUILTIN_MODULE` | 构建镜像必须是 `node:22-alpine`（pnpm@11），确认未误迁移 node:20 |
