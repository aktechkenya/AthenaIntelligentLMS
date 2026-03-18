package com.athena.lms.accounting.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class TrialBalanceResponse {
    private Integer periodYear;
    private Integer periodMonth;
    private List<BalanceResponse> accounts;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private boolean balanced;
}
