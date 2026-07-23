package org.di.ai_investigator_report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.ai_investigator_report.dto.notification.ReportProcessingMessage;
import org.di.ai_investigator_report.dto.notification.ReportProcessingStatus;
import org.di.ai_investigator_report.dto.notification.ReportResultMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final RabbitTemplate rabbitTemplate;
    private final WebClient.Builder webClient;
    private final MinioService minioService;
    //убрал LogService

    @Value("${ai.model.url}")
    private String aiModelUrl;

    @Value("${report.port}")
    private String reportModelPort;

    @Value("${spring.rabbitmq.report.result.exchange}")
    private String RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.report.result.routing-key}")
    private String RESULT_ROUTING_KEY;

    public void processReport(ReportProcessingMessage msg) {
        long startTime = System.currentTimeMillis();
        String caseNumber = msg.getCaseNumber();

        notifyProcessing(msg);

        try {
            if (caseNumber == null || caseNumber.isBlank()) {
                throw new IllegalStateException("caseNumber is null or blank");
            }

            log.info("Report step 1: requesting report from AI model for case {}", caseNumber);

            byte[] docxBytes = webClient.build().post()
                    .uri(aiModelUrl + ":" + reportModelPort + "/api/report")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "rag_id", caseNumber,
                            "user_id", msg.getUserId()))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (docxBytes == null || docxBytes.length == 0) {
                throw new IllegalStateException("Пустой ответ от AI-сервиса для дела " + caseNumber);
            }

            log.info("Report step 2: received docx ({} bytes) for case {}", docxBytes.length, caseNumber);

            String storedFileName = UUID.randomUUID() + "_report.docx";
            String reportFileUrl = minioService.uploadReportFile(docxBytes, caseNumber, storedFileName);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("Report processing completed for case {} after {}s, stored as {}",
                    caseNumber, duration, storedFileName);

            notifyCompletion(msg, reportFileUrl, storedFileName, duration);

        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("Report processing failed for case {} after {}s: {}",
                    caseNumber, duration, e.getMessage(), e);
            notifyFailure(msg, e.getMessage(), duration);
        }
    }

    public void notifyProcessing(ReportProcessingMessage msg) {
        sendNotification(ReportResultMessage.builder()
                .caseNumber(msg.getCaseNumber())
                .userEmail(msg.getUserEmail())
                .status(ReportProcessingStatus.PROCESSING)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public void notifyCompletion(ReportProcessingMessage msg, String reportFileUrl,
                                 String fileName, long duration) {
        sendNotification(ReportResultMessage.builder()
                .caseNumber(msg.getCaseNumber())
                .userEmail(msg.getUserEmail())
                .status(ReportProcessingStatus.COMPLETED)
                .reportFileUrl(reportFileUrl)
                .fileName(fileName)
                .timestamp(LocalDateTime.now())
                .processingDurationSeconds(duration)
                .build());
    }

    public void notifyFailure(ReportProcessingMessage msg, String errorMessage, long duration) {
        sendNotification(ReportResultMessage.builder()
                .caseNumber(msg.getCaseNumber())
                .userEmail(msg.getUserEmail())
                .status(ReportProcessingStatus.FAILED)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .processingDurationSeconds(duration)
                .build());
    }

    private void sendNotification(ReportResultMessage message) {
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                rabbitTemplate.convertAndSend(RESULT_EXCHANGE, RESULT_ROUTING_KEY, message);
                log.debug("Sent {} notification for case {}",
                        message.getStatus(), message.getCaseNumber());
                return;
            } catch (Exception e) {
                retryCount++;
                log.error("Failed to send notification (attempt {}/{}): {}",
                        retryCount, maxRetries, e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000L * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}