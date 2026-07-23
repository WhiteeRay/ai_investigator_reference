package org.di.ai_investigator_report.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportProcessingMessage {
    private String caseNumber;
    private String userEmail;
    private Long userId;
}
