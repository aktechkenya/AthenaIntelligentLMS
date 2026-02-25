package com.athena.lms.product.dto.response;

import com.athena.lms.product.entity.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
public class ProductResponse {

    private UUID id;
    private String tenantId;
    private String productCode;
    private String name;
    private String productType;
    private String status;
    private String description;
    private String currency;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer minTenorDays;
    private Integer maxTenorDays;
    private String scheduleType;
    private String repaymentFrequency;
    private BigDecimal nominalRate;
    private BigDecimal penaltyRate;
    private int penaltyGraceDays;
    private int gracePeriodDays;
    private BigDecimal processingFeeRate;
    private int version;
    private String templateId;
    private boolean requiresTwoPersonAuth;
    private boolean pendingAuthorization;
    private String createdBy;
    private List<ProductFeeResponse> fees;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product p) {
        List<ProductFeeResponse> fees = p.getFees() == null ? List.of() :
                p.getFees().stream().map(ProductFeeResponse::from).collect(Collectors.toList());
        return ProductResponse.builder()
                .id(p.getId())
                .tenantId(p.getTenantId())
                .productCode(p.getProductCode())
                .name(p.getName())
                .productType(p.getProductType().name())
                .status(p.getStatus().name())
                .description(p.getDescription())
                .currency(p.getCurrency())
                .minAmount(p.getMinAmount())
                .maxAmount(p.getMaxAmount())
                .minTenorDays(p.getMinTenorDays())
                .maxTenorDays(p.getMaxTenorDays())
                .scheduleType(p.getScheduleType().name())
                .repaymentFrequency(p.getRepaymentFrequency().name())
                .nominalRate(p.getNominalRate())
                .penaltyRate(p.getPenaltyRate())
                .penaltyGraceDays(p.getPenaltyGraceDays())
                .gracePeriodDays(p.getGracePeriodDays())
                .processingFeeRate(p.getProcessingFeeRate())
                .version(p.getVersion())
                .templateId(p.getTemplateId())
                .requiresTwoPersonAuth(p.isRequiresTwoPersonAuth())
                .pendingAuthorization(p.isPendingAuthorization())
                .createdBy(p.getCreatedBy())
                .fees(fees)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
