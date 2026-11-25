# 生产环境部署路径总览

集群拓扑：外网 → 171 网关（least_conn）→ 4 个后端实例；MySQL/Redis 在 223，MinIO 在 162。

```
用户 → 60.217.79.104:8088 (171 nginx 源码安装)
         ├─ /            → 127.0.0.1:8089 (bzpt-web 前端容器, 内部端口)
         ├─ /prod-api/   → 32:8081 32:8082 211:8081 211:8082 (least_conn)
         ├─ /uploads/    → 同上
         └─ /prod-api/resource/sse → 同上(独立 upstream, ip_hash)
后端 → 223 MySQL:3306 / Redis:6379
后端 → 162 MinIO:9000
```

## 一、服务器与角色

| 主机 | 私网 IP | 角色 | 组件 |
|---|---|---|---|
| 260929test2-0001 | 192.168.0.171（公网 60.217.79.104） | 接入层 | Nginx 1.30.4（对外 8088）+ `bzpt-web`（内部 8089） |
| 260929test2-0002 | 192.168.0.32 | 应用层 | `bzpt-server-8081`/`8082` |
| 260929test2-0003 | 192.168.0.211 | 应用层 | `bzpt-server-8081`/`8082` |
| 260929test2-0004 | 192.168.0.223 | 数据层 | `bzpt-mysql`(3306) + `bzpt-redis`(6379) |
| 260929test1 | 192.168.0.162 | 存储层 | `bzpt-minio`(9000/9001)，2TB |
| 260902test | 192.168.0.50 | 镜像源 | 基础镜像来源（不跑业务） |

统一 OS：openEuler 24.03 LTS，8C16G；数据盘 171/32/211/223 各 500G、162 为 2T。

## 二、接入层 192.168.0.171

| 项 | 路径 / 值 |
|---|---|
| Nginx 安装目录 | `/usr/local/nginx/`（源码编译，prefix） |
| 主配置 | `/usr/local/nginx/conf/nginx.conf`（第 18 行 `include /usr/local/nginx/conf/conf.d/*.conf;`） |
| 网关配置 | `/usr/local/nginx/conf/conf.d/nginx-gw.conf` |
| 日志 | `/usr/local/nginx/logs/access.log`、`error.log` |
| systemd | `/etc/systemd/system/nginx.service` |
| 前端容器 | `bzpt-web`，host 网络，**内部 8089**（由网关 8088 反代）；静态资源 `/usr/share/nginx/html` |
| 前端站点配置 | 模板 `/etc/nginx/conf.d/default.conf.template` → 渲染为 `default.conf` |
| 前端注入变量 | `PROD_API_TARGET=http://127.0.0.1/prod-api/`、`UPLOADS_TARGET=http://127.0.0.1/uploads/` |
| 四层转发(stream) | `/usr/local/nginx/conf/stream.d/db-oss.conf`：3306→223:3306、9000→162:9000、9001→162:9001 |
| 上传/发布目录 | `/data/deploy`（镜像包、配置模板） |
| 生效命令 | `nginx -t && systemctl reload nginx` |

> nginx 的 `include` 相对路径基准是 conf 目录，必须写绝对路径，否则 `conf/conf.d` 会被解析到 `conf/conf/conf.d` 并静默失效。

## 三、应用层 192.168.0.32 / 192.168.0.211（两台相同）

| 项 | 路径 / 值 |
|---|---|
| 容器 | `bzpt-server-8081`、`bzpt-server-8082`（host 网络，`--restart unless-stopped`） |
| 外置配置目录 | `/data/bzpt/config/` → 容器 `/ruoyi/server/config` |
| 配置文件 | `application.yml`、`application-dev.yml`、`application-local.yml`、`application-prod.yml` |
| 日志 | `/data/bzpt/logs-8081`、`/data/bzpt/logs-8082` → 容器 `/ruoyi/server/logs` |
| 环境变量 | `SERVER_PORT`、`SNAIL_PORT=2${port}`、`JAVA_OPTS=-Xms1g -Xmx4g -XX:+UseZGC` |
| 生效命令 | 改配置后 `docker restart bzpt-server-8081 bzpt-server-8082` |

> 配置目录是**各机本地**的，改配置需同时改 32 和 211（或改完 scp 同步），否则两机行为不一致。
> 端口靠 `-Dserver.port` 命令行参数覆盖，优先级高于 yml，因此 4 个实例可共用同一份配置。

## 四、数据层 192.168.0.223

| 组件 | 数据目录 | 端口 | 关键参数 |
|---|---|---|---|
| `bzpt-mysql` | `/data/mysql` | 3306 | 库 `ry-vue`，root 口令见 env.sh `MYSQL_ROOT_PWD`，utf8mb4，buffer_pool 6G |
| `bzpt-redis` | `/data/redis` | 6379 | 口令见 env.sh `REDIS_PWD`，AOF，`maxmemory 4gb`，`noeviction` |
| SQL 脚本归档 | `/data/deploy/sql/`（001…043 按序） | — | 主库 → label 增量 → update |

## 五、存储层 192.168.0.162

| 项 | 路径 / 值 |
|---|---|
| 容器 | `bzpt-minio`，host 网络 |
| 数据目录 | `/data/minio1`…`/data/minio4`（4 目录 erasure，共 2TB） |
| 端口 | 9000（API）、9001（控制台） |
| 凭据 / 桶 | 见 env.sh `MINIO_USER` / `MINIO_PWD`，桶 `mark-server`（应用自动创建） |

## 六、Docker（5 台通用）

| 项 | 路径 |
|---|---|
| 二进制 | `/usr/bin/`（`docker`、`dockerd`、`containerd`、`runc`…） |
| systemd 单元 | `/etc/systemd/system/docker.service`、`containerd.service` |
| 守护进程配置 | `/etc/docker/daemon.json`（`data-root=/data/docker`，日志 100m×3，live-restore） |
| 镜像/容器数据 | `/data/docker` |
| 数据盘 | `/dev/vdb` → `/data`（xfs，已写入 `/etc/fstab`） |

## 七、脚本与配置模板（仓库内）

| 位置 | 说明 |
|---|---|
| `deploy/prod/env.sh` | 服务器 IP、端口、镜像名、组件口令、远程执行封装 |
| `deploy/prod/00-setup-ssh.sh` | 本地 → 171 → 内网机 SSH 免密 |
| `deploy/prod/01-install-docker.sh` | docker 静态包离线安装、数据盘挂载、systemd 与 daemon.json |
| `deploy/prod/02-install-nginx.sh` | nginx 源码编译安装与网关配置 |
| `deploy/prod/10-fetch-base-images.sh` | 从 192.168.0.50 迁移 mysql/redis/minio 镜像 |
| `deploy/prod/11-prepare-base-images.sh` | 基础镜像准备：**本地**拉取 JDK17 → 上传 171 后 docker load + 打别名 |
| `deploy/prod/tools/pull-base-image.mjs` | 本机无 docker 时，直连 registry 下载镜像并打包成 `docker save` 格式 |
| `deploy/prod/20-deploy-minio.sh` | MinIO（162） |
| `deploy/prod/21-deploy-mysql-redis.sh` | MySQL + Redis（223），`--with-sql` 按序导入脚本 |
| `deploy/prod/30-deploy-backend.sh` | 后端发布：本地打 jar 包 → 上传 171 构建镜像 → 内网分发应用机滚动发布 |
| `deploy/prod/31-deploy-frontend.sh` | 前端发布：本地打 dist → 上传 171 构建镜像并启动容器 |
| `deploy/prod/12-prepare-builder-images.sh` | **CI 方案**（见第十二节）：一次性准备 171 的构建容器镜像与只读 Deploy Key |
| `deploy/prod/50-ci-deploy.sh` | **CI 方案**：本机触发器，同步脚本并触发 171 上的远端构建发布 |
| `deploy/prod/51-rollback.sh` | **CI 方案**：复用构建机上的历史 commit 镜像回滚 |
| `deploy/prod/ci/ci.env.sh`、`build-backend.sh`、`build-frontend.sh` | **CI 方案**：跑在 171 上的服务端配置与构建脚本 |
| `deploy/prod/40-backup.sh` | 备份 MySQL / MinIO / 外置配置，`--install-cron` 安装每日定时任务 |
| `deploy/prod/90-status.sh` | 全链路巡检 |
| `deploy/prod/conf/nginx-gw.conf` | 网关配置模板（least_conn + SSE ip_hash + actuator 403） |
| `deploy/prod/conf/application*.yml` | 生产配置模板（已指向 223 / 162） |
| `deploy.sh`、`deploy-remote.sh` | **单机 dev 版，保留未改动** |
| 前端 `Dockerfile`、`nginx.conf`、`docker-entrypoint.sh` | 前端镜像与代理地址注入 |

## 八、部署顺序

```bash
cd deploy/prod
BZPT_SSH_PASS='服务器密码' bash 00-setup-ssh.sh   # 首次: 配免密
bash 01-install-docker.sh all --mkfs              # 离线装 Docker(数据盘未格式化时加 --mkfs)
bash 02-install-nginx.sh                          # 171 编译安装 nginx + 网关
bash 10-fetch-base-images.sh list                 # 查看 .50 上的基础镜像 tag
bash 10-fetch-base-images.sh sync all             # 迁移 mysql/redis → 223, minio → 162
bash 11-prepare-base-images.sh                    # 基础镜像: 本地拉 JDK17 → 上传 171 载入(只需一次)
bash 20-deploy-minio.sh                           # MinIO
bash 21-deploy-mysql-redis.sh --with-sql          # MySQL + Redis + 导入 SQL
bash 30-deploy-backend.sh --build                 # 后端滚动发布(32 → 211)
bash 31-deploy-frontend.sh                        # 前端
bash 90-status.sh                                 # 巡检
bash 40-backup.sh --install-cron                  # 安装每日 02:00 备份任务
```

## 九、日常发布（单独部署前端 / 后端）

前后端可完全独立发布，互不影响。以下命令均在 `deploy/prod` 目录下执行。

### 基础镜像准备（首次部署或需要更换 JDK 镜像时）

后端原 `bzpt-java17`（liberica）已弃用，改用 **eclipse-temurin:17-jre-jammy**（Adoptium 官方 JRE17）。
流程：**本地拉取 → 上传 171 → `docker load` → tag 成 `bzpt-java17:17.0.11-cds` 别名**，服务器全程不联网拉镜像。

```bash
bash 11-prepare-base-images.sh            # 默认只准备 JDK17(171 已有 nginx 镜像, 直接复用)
REGISTRY=docker.m.daocloud.io bash 11-prepare-base-images.sh          # docker.io 不通时走镜像站
JAVA_PULL_IMG=eclipse-temurin:17-jdk-jammy bash 11-prepare-base-images.sh   # 需要完整 JDK(在线诊断)时
bash 11-prepare-base-images.sh nginx      # 仅当 171 缺少 nginx 镜像时才需要
```

- 本机**装有 docker**：脚本走 `docker pull` + `docker save`；
- 本机**没有 docker**：自动改用 `tools/pull-base-image.mjs` 直连 registry 下载（只需 node + tar）；
- 解析结果写入 171 的 `/data/deploy/base-images.env`，30/31 构建时自动读取并传给 `--build-arg`。

### 只部署后端

```bash
bash 30-deploy-backend.sh           # 使用已有 jar 重新打镜像并发布
bash 30-deploy-backend.sh --build   # Java 代码有改动，先 mvn package 再发布
APP_HOSTS=192.168.0.32 bash 30-deploy-backend.sh   # 只发布指定机器
bash 30-deploy-backend.sh --force-conf             # 用随包配置覆盖服务器外置配置(慎用)
```

流程：**本地**把 jar + Dockerfile 打成上下文包 → 上传到 171 → 在 171 上 `docker build` →
由 171 内网分发镜像包到应用机 → **逐台滚动**
（32:8081 → 32:8082 → 211:8081 → 211:8082）。每台 `/auth/tenant/list` 返回 200 才继续下一台，
失败立即中止（其余机器保持原版本），发布结束后再从网关做一次跨机可达校验。

### 只部署前端

```bash
bash 31-deploy-frontend.sh            # 构建 dist → 打镜像 → 发布到 171
bash 31-deploy-frontend.sh --install  # 依赖有变化时强制 npm install
```

前端只跑在 171 一台：**本地**构建 dist 并随 Dockerfile/nginx.conf 打包上传 → 在 171 上构建镜像 →
重建 `bzpt-web`（内部 8089），由网关 8088 对外。本机没有 mvn/docker 也能发布前端（只需 node）。

### 只改配置（无需重新构建）

```bash
# 登录两台应用机(经 171 中转)，修改外置配置
vim /data/bzpt/config/application-dev.yml
docker restart bzpt-server-8081 bzpt-server-8082
```

> 配置目录是各机本地的，32 和 211 都要改，否则两台行为不一致。

### dev 单机环境（60.217.78.187）

```bash
cd /Users/zht/demo-bz/bzpt-backend && bash deploy.sh   # 后端
cd /Users/zht/demo-bz/bz-frontend  && bash deploy.sh   # 前端
```

根目录这两个是单机版，与 `deploy/prod/` 互不干扰。

### 发布后验证

```bash
bash 90-status.sh    # 4 个实例、网关、外网入口、MySQL / Redis / MinIO 全链路巡检
```

### 回滚

镜像 tag 固定为 `5.X`，回滚方式为代码回退后重新发布：

```bash
git checkout <上一个 commit> -- <改动文件>
bash 30-deploy-backend.sh --build      # 或 bash 31-deploy-frontend.sh
```

数据库变更不在自动回滚范围内，重要发布前先执行 `bash 40-backup.sh mysql`。

### 场景对照

| 改动内容 | 命令 | 走 CI（推荐，见第十二节） |
|---|---|---|
| 后端 Java 代码 | `bash 30-deploy-backend.sh --build` | `bash 50-ci-deploy.sh backend <分支>` |
| 前端代码 | `bash 31-deploy-frontend.sh` | `bash 50-ci-deploy.sh frontend <分支>` |
| 后端配置（地址、密码、参数） | 改 `/data/bzpt/config/*.yml` + `docker restart`，或用 `30` 带 `--force-conf` | 同左（配置不在 git 里） |
| 网关配置（路由、超时、upstream） | 改 `conf/nginx-gw.conf` + `bash 02-install-nginx.sh --reconf` | 同左 |
| 前端依赖 | `bash 31-deploy-frontend.sh --install` | `bash 50-ci-deploy.sh frontend <分支>`（服务端自动 reinstall） |

前置条件：本机 Docker 需运行；后端发布需要 Maven，前端发布需要 Node；未配置 SSH 免密时用
`BZPT_SSH_PASS='密码' bash 30-deploy-backend.sh` 这种方式带上密码。

## 十、常用运维

```bash
# 全链路巡检
cd deploy/prod && bash 90-status.sh

# 登录内网机(171 的 sshd 未开放 TCP 转发, 不能用 ssh -J)
ssh root@60.217.79.104 → ssh root@192.168.0.32

# 改后端配置(两台应用机都要改)
vim /data/bzpt/config/application-dev.yml
docker restart bzpt-server-8081 bzpt-server-8082

# 改网关
vim /usr/local/nginx/conf/conf.d/nginx-gw.conf && nginx -t && systemctl reload nginx

# 看日志
docker logs -f --tail 200 bzpt-server-8081

# 手动备份(定时任务每日 02:00 已自动执行 all)
cd deploy/prod && bash 40-backup.sh all           # mysql + minio + conf
bash 40-backup.sh mysql                           # 仅备份 MySQL
```

## 十一、备份

| 对象 | 方式 | 存放位置 | 保留 |
|---|---|---|---|
| MySQL `ry-vue` | `mysqldump --single-transaction`（utf8mb4，不锁表）后 gzip | `223:/data/backup/mysql/`，并同步一份到 `162:/data/backup/mysql/` | 7 天 |
| MinIO | 数据目录硬链接快照 `cp -al`（秒级，几乎不占空间） | `162:/data/backup/minio/minio{1..4}_<时间戳>/` | 7 天 |
| 外置配置 / 网关配置 | 目录复制 | `171:/data/backup/conf/<主机>_<时间戳>/config`、`nginx-gw_<时间戳>.conf` | 7 天 |

- 定时任务安装在 171：`0 2 * * * bash /data/deploy/40-backup.sh all`，日志 `171:/data/backup/backup.log`
- 保留天数与执行时间通过 `env.sh` 的 `KEEP_DAYS`、`CRON_TIME` 调整
- **注意**：MinIO 镜像内无 `mc` 且外网不可达，故用文件级硬链接快照，非对象级备份；恢复时需停止 MinIO 后用快照目录替换数据目录。若需要 `mc mirror` 级备份，可离线导入 `mc` 二进制后替换 `backup_minio()` 实现
- MySQL 未开启 binlog，当前为每日全量；如需时间点恢复（PITR），可在 `21-deploy-mysql-redis.sh` 的启动参数加 `--log-bin=/var/lib/mysql/mysql-bin --server-id=1` 并重建容器（数据目录保留）

## 十二、CI 远端构建发布（推荐）

前面 30/31 是「本地打包 + 171 构建」：本机仍要装 JDK / Maven / Node，还要 6GB 以上内存跑 vite，
换台机器就容易失败。这一节是**本机零依赖**方案：本机只做「触发」，
**拉代码 / Maven / Node 构建 / docker build / 分发 / 滚动发布全部在 171 上用容器完成**。

本机前置条件：**只要 `ssh` + Git Bash**（Windows 下 `C:\Program Files\Git\bin\bash.exe`）。
不需要 JDK、Maven、Node、docker，也不需要任何本地基础镜像。

| 位置 | 说明 |
|---|---|
| `deploy/prod/12-prepare-builder-images.sh` | 一次性准备：生成只读 Deploy Key + 拉取/构建容器镜像（`maven:3.9-eclipse-temurin-17`、`node:22-alpine`、`bzpt-git:1.0`） |
| `deploy/prod/ci/ci.env.sh` | 服务端配置：目录、仓库地址、缓存卷、`git_run` 容器封装、发布锁 |
| `deploy/prod/ci/build-backend.sh` | 跑在 171：拉码 → Maven 容器打包 → docker build → 分发 → 滚动发布 + 健康检查 |
| `deploy/prod/ci/build-frontend.sh` | 跑在 171：拉码 → Node 容器建 dist → docker build → 重启 `bzpt-web` + 校验 |
| `deploy/prod/50-ci-deploy.sh` | **本机唯一入口**：md5 比对增量同步 ci 脚本 → 触发远端构建 → 实时回显日志 |
| `deploy/prod/51-rollback.sh` | 回滚：复用构建机上历史 commit 镜像，不重新编译 |

### 一次性准备

1. 在 Gitee 两个仓库加只读公钥（`12` 脚本会生成并打印）：
   `仓库 → 管理 → Deploy Keys 管理 → 添加`，**不要勾选可写**。
   私钥只存在 `171:/data/deploy/ci/deploy_key`（600，不进代码库），吊销时在 Gitee 删掉即可。
2. 准备构建镜像：

```bash
bash 12-prepare-builder-images.sh             # 走镜像站 docker pull
bash 12-prepare-builder-images.sh --offline   # 外网不通时, 用 tools/pull-base-image.mjs 下载后离线 load
```

### 日常发布

```bash
bash 50-ci-deploy.sh backend  master            # 后端: 拉 master 最新并发布
bash 50-ci-deploy.sh frontend feature-1030      # 前端: 指定分支
bash 50-ci-deploy.sh all      master            # 前后端一起
bash 50-ci-deploy.sh backend  master  abc1234   # 发布到指定 commit
```

### 回滚

镜像除 `5.X` 外还会打 `<短 commit>` 标签，回滚就是把这个历史镜像重新分发一遍：

```bash
bash 51-rollback.sh list                 # 查看构建机上可回滚的版本
bash 51-rollback.sh backend  f4c3275     # 后端回滚(不重新编译)
bash 51-rollback.sh frontend 884f4cb     # 前端回滚
```

> 前提是该 commit **曾被 `50-ci-deploy.sh` 构建过**；数据库变更不在自动回滚范围内，重要发布前先 `bash 40-backup.sh mysql`。

### 查看部署进度与日志

三种方式，按场景选：

**方式一：本机窗口实时看（最常用）**

```bash
bash 50-ci-deploy.sh backend master     # 进度直接回显到本机终端
```

由 `exec > >(tee "$RUN_LOG") 2>&1` 实现一边回显一边落盘。认这几个阶段标记：

| 阶段 | 含义 |
|---|---|
| `[1/3] 同步 CI 脚本` | md5 比对增量上传（`未变化` / `已更新`） |
| `[2/3] 触发后端/前端` | 已连到 171，开始远端构建 |
| `[1/6] 检出代码` | 容器 git clone/fetch + reset 到指定分支或 commit |
| `[2/6] Maven 构建` | 容器内 `mvn package`（**首次拉依赖慢**，之后约 8 秒） |
| `[3/6] 构建镜像` | docker build + 打 `<短 commit>` 标签 |
| `[4/6] 分发镜像包` | scp 到 32 / 211（每张机约 230MB） |
| `[5/6] 滚动发布` | **重点段**：逐台 `-- 重启实例 8081 --` → `健康检查通过` → `从网关可达`，失败即中止 |
| `[6/6]` / `===== 后端发布完成` | 完成，并打印本次日志路径 |

前端阶段是 `[1/4]`…`[4/4]`（Node 构建 dist → 构建镜像 → 重启 `bzpt-web`），比后端快。

**方式二：本机后台跑 + 另开窗口追**

```bash
# Git Bash / Linux
bash 50-ci-deploy.sh backend master > /tmp/backend-ci.log 2>&1 &
tail -f /tmp/backend-ci.log

# PowerShell
Get-Content .\backend-ci.log -Wait -Tail 20
```

**方式三：看 171 上的归档日志（事后复盘 / 本机窗口已关）**

```bash
ssh root@60.217.79.104

ls -1t /data/deploy/logs/                                   # 按时间倒序
tail -f "$(ls -1t /data/deploy/logs/backend-*.log  | head -1)"
tail -f "$(ls -1t /data/deploy/logs/frontend-*.log | head -1)"
```

- 文件名格式 `backend-<年月日-时分秒>.log` / `frontend-*.log`，例如 `backend-20261031-210404.log`
- **自动保留最近 20 份**（`ci.env.sh` 里 `KEEP_LOGS`），更早的自动清理——事后排查要趁早
- 构建细节另有一份：Maven 完整输出在 `171:/data/deploy/build/backend/mvn.log`（编译失败先查它）

### 卡住了怎么判断

| 现象 | 判断 / 处理 |
|---|---|
| 长时间只有第一行、无阶段推进 | 有人在构建，等 `flock` 发布锁（最多等 900 秒）；`ls -1t /data/deploy/logs/ \| head -1` 看是不是别人那轮 |
| 停在 `[2/6] Maven 构建` | 首次拉依赖或项目较大，`tail -f /data/deploy/build/backend/mvn.log` 看是否在下载 |
| 停在 `[4/6] 分发` | 内网传 230MB 包，正常几十秒；网络慢会更久 |
| `[5/6]` 报健康检查失败 | 已中止，其余机器保留旧版本；`docker logs --tail 100 bzpt-server-8081` 看启动异常 |
| 报出 `[ERR]` | 脚本 `die` 退出，同一行就是原因（如 Deploy Key 未授权、分支/commit 不存在） |

### 发布完怎么确认真的上线了

```bash
# 1) 本机最后一行应为 OK，且带 短commit
#    ===== 后端发布完成: bzpt-server:5.X @ f4c32755b (日志: /data/deploy/logs/backend-*.log) =====

# 2) 应用机: 容器创建时间应为刚刚(别只看 Up xx hours)
ssh root@60.217.79.104 'ssh root@192.168.0.32 "docker ps --format \"{{.Names}}|{{.Status}}\" | grep bzpt-server; docker inspect bzpt-server-8081 --format \"Created={{.Created}}\""'

# 3) 容器/业务日志
docker logs --tail 200 -f bzpt-server-8081
tail -f /data/bzpt/logs-8081/sys-console.log       # 按天切割、过期 gzip

# 4) 前端
docker logs -f bzpt-web                            # 171 上
```

> 提示：`docker ps` 的 `Up xx hours` 会误导（容器用同一名字重建时状态照旧刷新），
> 判断有没有换成新镜像要看 `Created` 或 `labels/Image` 与本机是否一致。

### 关键设计

| 项 | 做法 |
|---|---|
| 服务端不装任何工具链 | git / maven / node 全部以容器运行（`bzpt-git` 内置 git + openssh-client） |
| 依赖缓存 | 命名卷 `bzpt-m2`、`bzpt-node-modules`、`bzpt-pnpm-store`，首次之后 mvn 约 8 秒、pnpm 约 1.5 分钟 |
| 版本可追溯 | 镜像 tag = `5.X` + `<短 commit>`；每次发布日志落到 `171:/data/deploy/logs/{backend,frontend}-<时间戳>.log`，保留 20 份 |
| 并发保护 | `flock` 发布锁，等待 900 秒超时，避免多人同时构建互相覆盖 |
| 配置外置 | 沿用 `/data/bzpt/config`，首次用仓库 `deploy/prod/conf` 种子初始化，之后以服务器文件为准 |
| Node 版本 | 必须 `node:22-alpine`：项目 `packageManager` 是 `pnpm@11`，在 node:20 上会报 `ERR_UNKNOWN_BUILTIN_MODULE` |

> 踩过的坑：远端脚本内部不要依赖 ssh 的 stdin 透传（`-n` 会把 stdin 指向 `/dev/null`，
> 且脚本若由 stdin 喂给 `bash -s`，内部的 ssh 会把脚本剩余内容吞掉）。
> 统一做法是**先 scp 脚本到目标机，再 ssh 执行**。

### 换一台新电脑要准备什么

| # | 前置条件 | 缺失后果 |
|---|---|---|
| 1 | Git Bash（Windows）或任意 POSIX shell | 脚本起不来；**注意本机默认 `bash` 是未装分发的 WSL，不能用**，要用 `C:\Program Files\Git\bin\bash.exe` |
| 2 | 能连 `60.217.79.104:22` 且已 SSH 免密（`bash 00-setup-ssh.sh`） | `Permission denied`；密码重试过多还会触发服务器登录锁定 |
| 3 | Gitee 两个仓库已配置 171 的只读 Deploy Key | 拉码报 `Permission denied (publickey)` |
| 4 | 171 上执行过一次 `12-prepare-builder-images.sh` | maven / node / git 构建镜像不存在 |

不需要：JDK、Maven、Node、docker、本地基础镜像 —— **都没有也能发布**。

> Windows 提醒：HOME 路径含中文时，后台任务里 ssh 可能读不到密钥。先在 Git Bash 里确认
> `ssh root@60.217.79.104 hostname` 能直接返回主机名再跑发布。
> 仓库已带 `.gitattributes`，`.sh / Dockerfile / conf / yml` 强制 LF；
> 若本机 `core.autocrlf=true`，clone 后用 `git ls-files --eol deploy/prod` 确认是 `lf`。

### 排错

| 现象 | 原因 / 处理 |
|---|---|
| clone 报 `Permission denied (publickey)` | Deploy Key 没加到仓库或未启用；公钥要加到**后端和前端两个**仓库 |
| `DeployKey not found from project or not enabled` | 同上，逐个仓库确认（后端通过后前端也可能漏配） |
| 远端 Maven/Node 报 `pull access denied` 或找不到镜像 | 先跑 `bash 12-prepare-builder-images.sh` 准备构建镜像 |
| 前端构建报 `ERR_UNKNOWN_BUILTIN_MODULE` | 构建镜像必须是 `node:22-alpine`（项目 `packageManager` 是 `pnpm@11`） |
| 前端构建 OOM | 在 171 上构建，默认给 8GB 堆；仍不足可在触发时用 `NODE_HEAP=--max-old-space-size=12288` 调大 |
| 发布没动静 / 卡在等待 | 有人正在构建，`flock` 发布锁未释放（等待上限 900 秒）；可上 171 看 `/data/deploy/logs/*-<时间戳>.log` |
| 中途失败 | 滚动发布按机器串行且健康检查不通过会中止，已发机器保留新版本、未发机器仍是旧版本；确认后重跑或 `51-rollback.sh` 回滚 |

> 想确认刚才那次到底发成功没有：看本机回显的最后几行，或上 171 执行
> `ls -1t /data/deploy/logs/backend-*.log | head -1` 再 `tail -30 <该文件>`。

## 十三、路径速查（配置 / 日志 / 产物）

### 构建机 / 网关 171（60.217.79.104）

| 用途 | 路径 |
|---|---|
| CI 脚本 | `/data/deploy/ci/ci.env.sh`、`build-backend.sh`、`build-frontend.sh` |
| Git 只读凭据 | `/data/deploy/ci/deploy_key`（600）、`deploy_key.pub`（私钥不进代码库） |
| 源码检出 | `/data/deploy/src/bzpt-backend`、`/data/deploy/src/bz-frontend`（持久，每次 `git fetch` 增量更新） |
| 构建中间产物 | `/data/deploy/build/backend/`（`target/ruoyi-admin.jar`、`mvn.log`、`app-deploy.sh`）、`/data/deploy/build/frontend/`（dist）、`/data/deploy/build/gitctx/`（`bzpt-git` 镜像上下文） |
| **发布日志归档** | `/data/deploy/logs/backend-<时间戳>.log`、`frontend-<时间戳>.log`（自动保留最近 20 份） |
| 基础镜像变量 | `/data/deploy/base-images.env`（`RESOLVED_JAVA_IMG`、`RESOLVED_NGINX_IMG`，构建时读取） |
| 依赖缓存 | docker 命名卷 `bzpt-m2`（Maven）、`bzpt-node-modules` + `bzpt-pnpm-store`（前端） |
| 前端容器静态资源 | 容器内 `/usr/share/nginx/html`；站点配置 `/etc/nginx/conf.d/default.conf`（由 `default.conf.template` 渲染） |
| 网关日志 | `/usr/local/nginx/logs/access.log`、`error.log` |
| 旧方案遗留（第九节） | `/data/deploy/backend-ctx.tar.gz`、`frontend-ctx.tar.gz` 等，本地打包时用，发布后自动清理 |

### 应用机 192.168.0.32 / 192.168.0.211

| 用途 | 路径 |
|---|---|
| **外置配置** | `/data/bzpt/config/application{,-dev,-local,-prod}.yml` → 容器 `/ruoyi/server/config`（首次由种子初始化，之后以本地文件为准） |
| **应用日志** | `/data/bzpt/logs-8081/`、`/data/bzpt/logs-8082/` → 容器 `/ruoyi/server/logs`；主日志 `sys-console.log`，按天切割并 gzip 归档 |
| 容器日志 | `docker logs -f --tail 200 bzpt-server-8081` / `-8082` |
| 镜像包（分发临时） | `/data/deploy/bzpt-server-5.X.tar.gz`（`docker load` 后保留，下次分发覆盖） |
| 配置种子 | `/data/deploy/conf-seed/`（首次部署用来初始化 `/data/bzpt/config`） |
| 远端执行脚本 | `/data/deploy/app-deploy.sh`（每次发布由 171 scp 覆盖，勿手工改） |

> 两台应用机配置是**各自本地**的：改配置要 32、211 都改（或改完 scp 同步），否则两机行为不一致。

### 常用定位命令

```bash
# 最近一次后端 / 前端发布日志(在 171)
ls -1t /data/deploy/logs/backend-*.log  | head -1 | xargs tail -40
ls -1t /data/deploy/logs/frontend-*.log | head -1 | xargs tail -40

# 后端实时日志(经 171 跳到应用机)
ssh root@60.217.79.104 'ssh root@192.168.0.32 "docker logs -f --tail 200 bzpt-server-8081"'
ssh root@60.217.79.104 'ssh root@192.168.0.32 "tail -f /data/bzpt/logs-8081/sys-console.log"'

# Maven 构建日志(失败时重点看这个, 在 171)
tail -40 /data/deploy/build/backend/mvn.log

# 查看/修改外置配置
ssh root@60.217.79.104 'ssh root@192.168.0.32 "cat /data/bzpt/config/application-dev.yml"'
docker restart bzpt-server-8081 bzpt-server-8082          # 改完生效

# 依赖缓存卷(清理可重建, 但首次会重新下载)
ssh root@60.217.79.104 'docker volume ls | grep bzpt'
```

## 十四、约束与注意事项

1. **跳板方式**：内网机经 `ssh 171 "ssh <内网机> <命令>"` 两段式访问；171 的 `sshd_config` 为 `AllowTcpForwarding no`，`ssh -J` 不可用。
2. **公网端口暴露**：MySQL(3306)、MinIO(9000/9001) 已通过 171 的公网 IP 映射出去，仅用于管理维护。**必须在安全组或 `stream.d/db-oss.conf` 中限定来源 IP**（在 server 块加 `allow <IP>; deny all;`），不要对 `0.0.0.0/0` 开放，否则数据库将暴露在公网被暴力破解；这些流量还占用 171 的 50Mbps 带宽
3. **安全组**：联通云默认入方向全阻断，需显式放行——171 的 8088（公网入口，HTTPS 时再加 443）、223 的 3306/6379、162 的 9000/9001、32/211 的 8081/8082（内网段）。
4. **端口占用**：host 网络下网关与前端容器不能同时占用同一端口，故网关对外 8088、前端容器内部 8089；nginx 主配置里的默认 server（原 80 端口）已注释。
5. **SSE**：`SseEmitter` 存实例本地内存，网关对 `/prod-api/resource/sse` 单独用 `ip_hash`，否则多实例推送会丢。
6. **大文件**：网关与前端容器 `client_max_body_size` 均为 1024m，与后端 1GB post 限制对齐。
7. **口令**：MySQL / Redis / MinIO 口令集中在 `env.sh`（`MYSQL_ROOT_PWD`、`REDIS_PWD`、`MINIO_PWD`），生产上线前应替换为自有强口令；改 MinIO 需同时改容器环境变量与 `application-dev.yml`。
8. **单机版**：`deploy.sh` / `deploy-remote.sh` 仍为 dev 单机部署（60.217.78.187），与 `deploy/prod/` 互不影响。
