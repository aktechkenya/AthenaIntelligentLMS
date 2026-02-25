package com.athena.lms.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.accounting", "com.athena.lms.common"})
public class AccountingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountingServiceApplication.class, args);
    }
}
