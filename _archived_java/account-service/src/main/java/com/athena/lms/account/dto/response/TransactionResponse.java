package com.athena.lms.account.dto.response;

import com.athena.lms.account.entity.AccountTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String reference;
    private String description;
    private String channel;
    private LocalDateTime createdAt;

    public static TransactionResponse from(AccountTransaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .transactionType(txn.getTransactionType().name())
                .amount(txn.getAmount())
                .balanceAfter(txn.getBalanceAfter())
                .reference(txn.getReference())
                .description(txn.getDescription())
                .channel(txn.getChannel())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
