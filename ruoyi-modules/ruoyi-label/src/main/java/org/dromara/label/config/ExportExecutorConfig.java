package org.dromara.label.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 导出后台任务线程池
 * <p>
 * 数据集导出（查库装配合格数据 + ZIP 打包 + MinIO 上传）在后台线程执行，
 * create 接口先做校验与记录创建（status=processing）后立即返回，前端轮询导出任务状态。
 */
@Configuration
public class ExportExecutorConfig {

    /**
     * 导出处理线程池：核心 1、最大 2、队列 100；任务溢出时由调用线程兜底执行，避免丢任务。
     * <p>导出频率低且单任务耗时可控（文本打包 + 对象上传），无需大吞吐配置。</p>
     */
    @Bean(name = "labelExportExecutor")
    public ThreadPoolTaskExecutor labelExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("label-export-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
