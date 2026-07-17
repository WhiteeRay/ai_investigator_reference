package org.di.ai_investigator_report.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportGenerateRequest {
    @JsonProperty("case_number")
    private String caseNumber;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("file_name")
    private String fileName;
}
