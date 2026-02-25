package com.athena.lms.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.reporting", "com.athena.lms.common"})
@EnableScheduling
public class ReportingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
