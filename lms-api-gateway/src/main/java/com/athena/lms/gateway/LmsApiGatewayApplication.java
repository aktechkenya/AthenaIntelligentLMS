package com.athena.lms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class LmsApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmsApiGatewayApplication.class, args);
    }
}
