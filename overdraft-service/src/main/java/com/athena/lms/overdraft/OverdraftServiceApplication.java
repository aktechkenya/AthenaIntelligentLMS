package com.athena.lms.overdraft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.overdraft", "com.athena.lms.common"})
@EnableScheduling
public class OverdraftServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OverdraftServiceApplication.class, args);
    }
}
