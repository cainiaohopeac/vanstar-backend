package org.dromara.label.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 导入后台任务线程池
 * <p>
 * 大文件分片合并（completeMultipartUpload）与资产入库（zip 解压/对象复制）耗时较长，
 * 若在 HTTP 请求线程内同步执行会长时间占用请求连接。该类任务统一提交到本线程池异步执行，
 * complete 接口先做校验与状态抢占（uploading -> processing）后立即返回，前端轮询导入记录状态。
 */
@Configuration
public class ImportExecutorConfig {

    /**
     * 导入处理线程池：<b>单线程 + 队列</b> —— 导入任务排队逐个执行。
     * <p>原先核心 4 / 最大 8 的并发配置下，多个大 zip 同时解压入库会互相拖慢、
     * 且并发写库放大了编号/批次类竞态（上传编号撞唯一键就是在这里暴露的）。
     * 用户明确要求「批量导入排队、一个一个传」：导入本身是重 IO + 重 DB 操作，
     * 串行化的吞吐损失远小于并发互踩的代价；队列 2000 足够吸纳批量提交，
     * 溢出时由调用线程兜底执行，不丢任务。</p>
     */
    @Bean(name = "labelImportExecutor")
    public ThreadPoolTaskExecutor labelImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("label-import-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
