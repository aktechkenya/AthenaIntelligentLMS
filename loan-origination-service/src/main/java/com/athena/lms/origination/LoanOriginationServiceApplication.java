package com.athena.lms.origination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.origination", "com.athena.lms.common"})
@EnableScheduling
public class LoanOriginationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanOriginationServiceApplication.class, args);
    }
}
