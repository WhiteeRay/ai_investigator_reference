package org.di.ai_investigator_report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.ai_investigator_report.dto.notification.ReportProcessingMessage;
import org.di.ai_investigator_report.dto.notification.ReportProcessingStatus;
import org.di.ai_investigator_report.dto.notification.ReportResultMessage;
import org.di.ai_investigator_report.dto.request.ReportGenerateRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final RabbitTemplate rabbitTemplate;
    private final WebClient.Builder webClient;
    private final MinioService minioService;
    private final LogService logService;

    @Value("${ai.model.url}")
    private String aiModelUrl;

    @Value("${report.port}")
    private String reportModelPort;

    @Value("${spring.rabbitmq.report.result.exchange}")
    private String RESULT_EXCHANGE;

    @Value("${spring.rabbitmq.report.result.routing-key}")
    private String RESULT_ROUTING_KEY;

    public void processFile(byte[] fileBytes, String fileName,
                            String caseNumber, ReportProcessingMessage originalMessage) {
        long startTime = System.currentTimeMillis();
        notifyProcessing(originalMessage);

        try {
            log.info("Report step 1: uploading file {} to AI model for case {}", fileName, caseNumber);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() { return fileName; }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.APPLICATION_PDF);
            body.add("file", new HttpEntity<>(resource, fileHeaders));
            body.add("case_number", new HttpEntity<>(caseNumber));
            body.add("user_id", new HttpEntity<>(String.valueOf(originalMessage.getUserId())));

            byte[] docxBytes = webClient.build().post()
                    .uri(aiModelUrl + ":" + reportModelPort + "/api/report")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            log.info("Report step 2: received docx file ({} bytes) for case {}",
                    docxBytes != null ? docxBytes.length : 0, caseNumber);

            String storedFileName = UUID.randomUUID() + "_" + fileName.replace(".pdf", ".docx");
            String reportFileUrl = minioService.uploadReportFile(docxBytes, caseNumber, storedFileName);

            logService.saveFileContent(docxBytes, storedFileName);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("Report processing completed for case {} after {}s", caseNumber, duration);

            notifyCompletion(originalMessage, reportFileUrl, duration);

        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("Report processing failed for file {} in case {} after {}s: {}",
                    fileName, caseNumber, duration, e.getMessage());
            notifyFailure(originalMessage, e.getMessage(), duration);
        }
    }

    public void notifyProcessing(ReportProcessingMessage msg) {
        sendNotification(ReportResultMessage.builder()
                .fileId(msg.getFileId())
                .caseNumber(msg.getCaseNumber())
                .fileName(msg.getOriginalFileName())
                .userEmail(msg.getUserEmail())
                .status(ReportProcessingStatus.PROCESSING)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public void notifyCompletion(ReportProcessingMessage msg, String reportFileUrl, long duration) {
        sendNotification(ReportResultMessage.builder()
                .fileId(msg.getFileId())
                .caseNumber(msg.getCaseNumber())
                .fileName(msg.getOriginalFileName())
                .userEmail(msg.getUserEmail())
                .status(ReportProcessingStatus.COMPLETED)
                .reportFileUrl(reportFileUrl)
                .timestamp(LocalDateTime.now())
                .processingDurationSeconds(duration)
                .build());
    }

    public void notifyFailure(ReportProcessingMessage msg, String errorMessage, long duration) {
        sendNotification(ReportResultMessage.builder()
                .fileId(msg.getFileId())
                .caseNumber(msg.getCaseNumber())
                .fileName(msg.getOriginalFileName())
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
                log.debug("Sent {} notification for file {} in case {}",
                        message.getStatus(), message.getFileId(), message.getCaseNumber());
                return;
            } catch (Exception e) {
                retryCount++;
                log.error("Failed to send notification (attempt {}/{}): {}", retryCount, maxRetries, e.getMessage());
                if (retryCount < maxRetries) {
                    try { Thread.sleep(1000L * retryCount); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
