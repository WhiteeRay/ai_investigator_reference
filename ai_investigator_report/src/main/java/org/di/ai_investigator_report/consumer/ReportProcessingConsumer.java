package org.di.ai_investigator_report.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.ai_investigator_report.dto.notification.ReportProcessingMessage;
import org.di.ai_investigator_report.service.ReportService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportProcessingConsumer implements MessageListener {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message) {
        ReportProcessingMessage reportMessage;
        try {
            reportMessage = objectMapper.readValue(message.getBody(), ReportProcessingMessage.class);
        } catch (Exception e) {
            log.error("Could not parse report message: {}", e.getMessage(), e);
            return;
        }

        log.info("Received report task in case {} from {}",
                reportMessage.getCaseNumber(), reportMessage.getUserEmail());

        reportService.processReport(reportMessage);
    }
}