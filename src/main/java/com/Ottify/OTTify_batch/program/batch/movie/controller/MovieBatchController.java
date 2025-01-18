package com.Ottify.OTTify_batch.program.batch.movie.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MovieBatchController {

    private final JobLauncher jobLauncher;
    private final Job movieBasicSaveJob;

    @GetMapping("/movie/all")
    public ResponseEntity<Void> saveAll() {

        try{
            log.info("Starting exampleJob...");
            jobLauncher.run(movieBasicSaveJob, new JobParametersBuilder()
                    .addString("basic", "movie")

                    .toJobParameters());
            log.info("Job execution completed.");

            return ResponseEntity.ok().build();
        }catch (Exception e){
            return ResponseEntity.status(500).build();
        }
    }
}
