package com.Ottify.OTTify_batch.program.batch.tv.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TVBatchController {

    private final JobLauncher jobLauncher;
    private final Job tvBasicSaveJob;
    private final Job tvUpdateJob;

    @GetMapping("/tv/all")
    public ResponseEntity<Void> saveAll() {

        //batch 시작 한번만
        try{
            log.info("Starting exampleJob...");
            jobLauncher.run(tvBasicSaveJob, new JobParametersBuilder()
                    .addString("basic", "tv")

                    .toJobParameters());
            log.info("Job execution completed.");

            return ResponseEntity.ok().build();
        }catch (Exception e){
            return ResponseEntity.status(500).build();
        }
    }


    @GetMapping("/tv/update")
    public ResponseEntity<Void> update(@RequestParam("date") String date) {

        try{
            log.info("Starting updateJob...");
            jobLauncher.run(tvUpdateJob, new JobParametersBuilder()
                    .addString("date",date)
                    .toJobParameters());
            log.info("Job execution completed.");

            return ResponseEntity.ok().build();
        }catch (Exception e){
            return ResponseEntity.status(500).build();
        }
    }
    @Scheduled(cron = "0 0 6 * * ?")
    public void executeDailyTVJob()
            throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {

        String yesterDay = LocalDateTime.now().
                minusDays(1).
                format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        jobLauncher.run(tvUpdateJob,new JobParametersBuilder()
                .addString("date",yesterDay)
                .toJobParameters());

    }
}
