package com.athena.lms.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.management", "com.athena.lms.common"})
@EnableScheduling
public class LoanManagementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanManagementServiceApplication.class, args);
    }
}
