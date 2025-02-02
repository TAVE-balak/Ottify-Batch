package com.Ottify.OTTify_batch.program.batch.executor;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ProgramExecutor {

//    @Bean
//    public TaskExecutor apiExecutor() {
//        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
//        executor.setConcurrencyLimit(50);
//        return executor;
//    }

    @Bean
    public TaskExecutor apiExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(50);
        taskExecutor.setMaxPoolSize(50);
        taskExecutor.setQueueCapacity(0);
        taskExecutor.setThreadNamePrefix("Executor-Batch-");
        return taskExecutor;
    }
}
