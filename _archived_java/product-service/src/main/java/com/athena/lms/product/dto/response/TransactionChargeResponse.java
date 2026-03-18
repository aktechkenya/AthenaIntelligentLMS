package com.athena.lms.product.dto.response;

import com.athena.lms.product.entity.ChargeTier;
import com.athena.lms.product.entity.TransactionCharge;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
public class TransactionChargeResponse {

    private UUID id;
    private String chargeCode;
    private String chargeName;
    private String transactionType;
    private String calculationType;
    private BigDecimal flatAmount;
    private BigDecimal percentageRate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String currency;
    private boolean isActive;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private List<TierResponse> tiers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class TierResponse {
        private UUID id;
        private BigDecimal fromAmount;
        private BigDecimal toAmount;
        private BigDecimal flatAmount;
        private BigDecimal percentageRate;

        public static TierResponse from(ChargeTier t) {
            return TierResponse.builder()
                    .id(t.getId())
                    .fromAmount(t.getFromAmount())
                    .toAmount(t.getToAmount())
                    .flatAmount(t.getFlatAmount())
                    .percentageRate(t.getPercentageRate())
                    .build();
        }
    }

    public static TransactionChargeResponse from(TransactionCharge c) {
        return TransactionChargeResponse.builder()
                .id(c.getId())
                .chargeCode(c.getChargeCode())
                .chargeName(c.getChargeName())
                .transactionType(c.getTransactionType().name())
                .calculationType(c.getCalculationType().name())
                .flatAmount(c.getFlatAmount())
                .percentageRate(c.getPercentageRate())
                .minAmount(c.getMinAmount())
                .maxAmount(c.getMaxAmount())
                .currency(c.getCurrency())
                .isActive(c.isActive())
                .effectiveFrom(c.getEffectiveFrom())
                .effectiveTo(c.getEffectiveTo())
                .tiers(c.getTiers() != null
                        ? c.getTiers().stream().map(TierResponse::from).collect(Collectors.toList())
                        : List.of())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
