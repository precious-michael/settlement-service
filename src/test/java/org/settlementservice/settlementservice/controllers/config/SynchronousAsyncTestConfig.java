package org.settlementservice.settlementservice.controllers.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Replaces the real "file-processing" thread pool with a synchronous executor so
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} handlers run inline, on the
 * committing thread, before the triggering {@code mockMvc.perform(...)} call returns — making
 * the upload-processing pipeline deterministic to assert against without polling. Registering
 * this bean first lets {@code AsyncConfig}'s own bean (guarded by
 * {@code @ConditionalOnMissingBean(name = "file-processing")}) back off instead of colliding.
 */
@TestConfiguration
public class SynchronousAsyncTestConfig {

    @Bean(name = "file-processing")
    public TaskExecutor fileProcessingExecutor() {
        return new SyncTaskExecutor();
    }
}
