package com.athena.lms.scoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.scoring", "com.athena.lms.common"})
public class AiScoringServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiScoringServiceApplication.class, args);
    }
}
