package com.athena.lms.account.dto.response;

import com.athena.lms.account.entity.Account;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {

    private UUID id;
    private String accountNumber;
    private String customerId;
    private String accountType;
    private String status;
    private String currency;
    private int kycTier;
    private String accountName;
    private BalanceResponse balance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AccountResponse from(Account account) {
        BalanceResponse balance = null;
        if (account.getBalance() != null) {
            balance = BalanceResponse.builder()
                    .availableBalance(account.getBalance().getAvailableBalance())
                    .currentBalance(account.getBalance().getCurrentBalance())
                    .ledgerBalance(account.getBalance().getLedgerBalance())
                    .updatedAt(account.getBalance().getUpdatedAt())
                    .build();
        }
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .customerId(account.getCustomerId())
                .accountType(account.getAccountType().name())
                .status(account.getStatus().name())
                .currency(account.getCurrency())
                .kycTier(account.getKycTier())
                .accountName(account.getAccountName())
                .balance(balance)
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
