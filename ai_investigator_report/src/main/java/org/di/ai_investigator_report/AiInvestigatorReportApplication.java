package org.di.ai_investigator_report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@EnableRabbit
public class AiInvestigatorReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiInvestigatorReportApplication.class, args);
    }

}
