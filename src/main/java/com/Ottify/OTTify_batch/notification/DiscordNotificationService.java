package com.Ottify.OTTify_batch.notification;

import com.Ottify.OTTify_batch.notification.dto.BatchResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class DiscordNotificationService {

    @Value("${discord.webhook.url}")
    private String webhookUrl;
    public void sendErrorMessage(final String jobName, final String message){

        WebClient webClient =  WebClient.builder()
                .baseUrl(webhookUrl).build();

        webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BatchResultMessage.fail(jobName, message))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e-> log.info("실패 디스코드 메시지 전송 실패", e))
                .retry(3)
                .block();
    }

    public void sendSuccessMessage(final String jobName){

        WebClient webClient =  WebClient.builder()
                .baseUrl(webhookUrl).build();

        webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BatchResultMessage.success(jobName))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e-> log.info("성공 디스코드 메시지 전송 실패", e))
                .retry(3)
                .block();
    }

}
