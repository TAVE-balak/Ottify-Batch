package com.Ottify.OTTify_batch.program.batch.listener;

import com.Ottify.OTTify_batch.notification.DiscordNotificationService;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobListener implements JobExecutionListener {

    private final DiscordNotificationService discordService;

    @Override
    public void afterJob(JobExecution jobExecution) {

        String jobName = jobExecution.getJobInstance().getJobName();

        if (jobExecution.getStatus() == BatchStatus.FAILED) {

            String errorMessage = jobExecution.getStepExecutions().stream()
                    .flatMap(se -> se.getFailureExceptions().stream())
                    .map(this::format)
                    .collect(Collectors.joining("\n"));

            discordService.sendErrorMessage(jobName, errorMessage);
            return;
        }

        if(jobExecution.getStatus() == BatchStatus.COMPLETED){

            discordService.sendSuccessMessage(jobName);
        }
    }

    private String format(Throwable t) {

        Throwable root = NestedExceptionUtils.getMostSpecificCause(t);
        return """
            [Top]  %s: %s
            [Root] %s: %s
            """.formatted(
                t.getClass().getName(), t.getMessage(),
                root.getClass().getName(), root.getMessage()
        );
    }
}
