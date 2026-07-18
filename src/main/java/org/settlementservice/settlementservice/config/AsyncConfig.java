package org.settlementservice.settlementservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * {@code @ConditionalOnMissingBean} lets tests swap in a synchronous executor of the same
     * name (see {@code SynchronousAsyncTestConfig}) without needing bean-definition-overriding
     * enabled — {@code @Async("file-processing")} resolves its executor purely by bean name, so
     * whichever bean holds that name wins outright.
     */
    @Bean(name = "file-processing")
    @ConditionalOnMissingBean(name = "file-processing") // Only create this bean if there isn't already another bean named file-processing
    public TaskExecutor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("file-processing-");
        executor.initialize();
        return executor;
    }
}
