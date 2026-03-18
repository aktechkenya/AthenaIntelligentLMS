package com.athena.lms.account.dto.response;

import com.athena.lms.common.dto.PageResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class StatementResponse {

    private String accountNumber;
    private String customerName;
    private String currency;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private PageResponse<TransactionResponse> transactions;
}
