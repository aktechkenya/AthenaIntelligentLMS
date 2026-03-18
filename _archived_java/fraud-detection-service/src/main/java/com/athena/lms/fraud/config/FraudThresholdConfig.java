package com.athena.lms.fraud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "fraud.thresholds")
@Data
public class FraudThresholdConfig {

    private BigDecimal largeTransactionAmount = new BigDecimal("1000000");
    private int structuringWindowHours = 24;
    private BigDecimal structuringThreshold = new BigDecimal("1000000");
    private int velocityMaxTransactions1h = 10;
    private int velocityMaxTransactions24h = 50;
    private int velocityMaxApplications30d = 5;
    private int rapidTransferWindowMinutes = 15;
    private int dormantAccountDays = 180;
    private int earlyPayoffDays = 30;
    private int loanCyclingWindowDays = 7;
}
