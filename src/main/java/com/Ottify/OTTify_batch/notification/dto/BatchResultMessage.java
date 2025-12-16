package com.Ottify.OTTify_batch.notification.dto;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchResultMessage {

    private String content;
    private List<Embed> embeds;
    private static final String BATCH_SUCCESS_MESSAGE = "Batch 성공";
    private static final String BATCH_FAIL_MESSAGE = "Batch 실패";
    private static final String JOB_SUCCESS_MESSAGE = "JOB 성공";
    private static int BLUE = 0x0000FF;
    private static int RED = 0xFF0000;

    public static BatchResultMessage success(String jobName){

        return new BatchResultMessage(BATCH_SUCCESS_MESSAGE, List.of(new Embed(jobName, JOB_SUCCESS_MESSAGE, BLUE)));
    }

    public static BatchResultMessage fail(String jobName, String message){
        return new BatchResultMessage(BATCH_FAIL_MESSAGE, List.of(new Embed(jobName, message, RED)));
    }

}
