package com.athena.lms.collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.collections", "com.athena.lms.common"})
@EnableScheduling
public class CollectionsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollectionsServiceApplication.class, args);
    }
}
