package com.athena.mediaservice;

import com.athena.lms.common.auth.JwtUtil;
import com.athena.lms.common.auth.LmsJwtAuthenticationFilter;
import com.athena.lms.common.auth.MdcLoggingFilter;
import com.athena.lms.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.athena.mediaservice"})
@EnableDiscoveryClient
@EnableCaching
@Import({JwtUtil.class, MdcLoggingFilter.class, LmsJwtAuthenticationFilter.class, GlobalExceptionHandler.class})
public class MediaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}
