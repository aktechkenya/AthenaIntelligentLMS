package com.athena.lms.account.dto.response;

import com.athena.lms.account.entity.FundTransfer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransferResponse {

    private UUID id;
    private UUID sourceAccountId;
    private String sourceAccountNumber;
    private UUID destinationAccountId;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private String currency;
    private String transferType;
    private String status;
    private String reference;
    private String narration;
    private BigDecimal chargeAmount;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String failedReason;

    public static TransferResponse from(FundTransfer t) {
        return from(t, null, null);
    }

    public static TransferResponse from(FundTransfer t, String srcAccNum, String destAccNum) {
        return TransferResponse.builder()
                .id(t.getId())
                .sourceAccountId(t.getSourceAccountId())
                .sourceAccountNumber(srcAccNum)
                .destinationAccountId(t.getDestinationAccountId())
                .destinationAccountNumber(destAccNum)
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .transferType(t.getTransferType().name())
                .status(t.getStatus().name())
                .reference(t.getReference())
                .narration(t.getNarration())
                .chargeAmount(t.getChargeAmount())
                .initiatedAt(t.getInitiatedAt())
                .completedAt(t.getCompletedAt())
                .failedReason(t.getFailedReason())
                .build();
    }
}
