package com.Ottify.OTTify_batch.program.batch.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class ProgramExecutor {

    @Bean
    public TaskExecutor apiExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(50); // 동시 실행 스레드 제한
        return executor;
    }
}
