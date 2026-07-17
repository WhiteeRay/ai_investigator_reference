package org.di.ai_investigator_report.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.ai_investigator_report.dto.notification.ReportProcessingMessage;
import org.di.ai_investigator_report.service.MinioService;
import org.di.ai_investigator_report.service.ReportService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportProcessingConsumer implements MessageListener {

    private final MinioService minioService;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message) {
        try {
            ReportProcessingMessage reportMessage = objectMapper.readValue(
                    message.getBody(), ReportProcessingMessage.class);

            log.info("Received report task: {} in case {} from {}",
                    reportMessage.getOriginalFileName(), reportMessage.getCaseNumber(), reportMessage.getUserEmail());

            InputStream fileStream = minioService.downloadFile(reportMessage.getFileUrl());
            byte[] fileBytes = fileStream.readAllBytes();

            reportService.processFile(
                    fileBytes,
                    reportMessage.getOriginalFileName(),
                    reportMessage.getCaseNumber(),
                    reportMessage
            );

        } catch (Exception e) {
            log.error("Failed to process report message: {}", e.getMessage());
            try {
                ReportProcessingMessage reportMessage = objectMapper.readValue(
                        message.getBody(), ReportProcessingMessage.class);
                reportService.notifyFailure(reportMessage, e.getMessage(), 0);
            } catch (Exception ex) {
                log.error("Could not parse message for failure notification: {}", ex.getMessage());
            }
        }
    }
}
