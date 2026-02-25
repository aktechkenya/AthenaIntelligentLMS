package com.athena.lms.floatmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.athena.lms.floatmgmt", "com.athena.lms.common"})
@EnableScheduling
public class FloatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FloatServiceApplication.class, args);
    }
}
