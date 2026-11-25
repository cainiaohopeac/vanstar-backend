# JDK17 基础镜像: 通过构建参数注入, 便于换源而无需改本文件
#   默认 bzpt-java17:17.0.11-cds(服务器上由 11-prepare-base-images.sh tag 出来的别名)
#   实际来源镜像见 deploy/prod/env.sh 的 JAVA_PULL_IMG(默认 eclipse-temurin:17-jre-jammy)
#   构建: docker build --build-arg JAVA_BASE_IMG=eclipse-temurin:17-jre-jammy ...
ARG JAVA_BASE_IMG=bzpt-java17:17.0.11-cds
FROM ${JAVA_BASE_IMG}
# 备选: eclipse-temurin:17-jdk-jammy / bellsoft/liberica-openjdk-debian:17.0.11-cds
#FROM findepi/graalvm:java17-native

LABEL maintainer="Lion Li"

RUN mkdir -p /ruoyi/server/logs \
    /ruoyi/server/temp \
    /ruoyi/skywalking/agent

WORKDIR /ruoyi/server

ENV SERVER_PORT=8080 SNAIL_PORT=28080 LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""

EXPOSE ${SERVER_PORT}
# 暴露 snail job 客户端端口 用于定时任务调度中心通信
EXPOSE ${SNAIL_PORT}

ADD ./target/ruoyi-admin.jar ./app.jar

SHELL ["/bin/bash", "-c"]

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} \
           -Dsnail-job.port=${SNAIL_PORT} \
           # 应用名称 如果想区分集群节点监控 改成不同的名称即可
           #-Dskywalking.agent.service_name=ruoyi-server \
           #-javaagent:/ruoyi/skywalking/agent/skywalking-agent.jar \
           -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} \
           -jar app.jar

