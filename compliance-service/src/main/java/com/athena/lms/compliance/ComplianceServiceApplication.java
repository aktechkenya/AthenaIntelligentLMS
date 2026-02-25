package com.athena.lms.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.compliance", "com.athena.lms.common"})
public class ComplianceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComplianceServiceApplication.class, args);
    }
}
