package com.athena.lms.origination.dto.response;

import com.athena.lms.origination.enums.ApplicationStatus;
import com.athena.lms.origination.enums.RiskGrade;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class ApplicationResponse {
    private UUID id;
    private String tenantId;
    private String customerId;
    private UUID productId;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private String currency;
    private Integer tenorMonths;
    private String purpose;
    private ApplicationStatus status;
    private RiskGrade riskGrade;
    private Integer creditScore;
    private BigDecimal interestRate;
    private BigDecimal disbursedAmount;
    private OffsetDateTime disbursedAt;
    private String disbursementAccount;
    private String reviewNotes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<CollateralResponse> collaterals;
    private List<NoteResponse> notes;
    private List<StatusHistoryResponse> statusHistory;
}
