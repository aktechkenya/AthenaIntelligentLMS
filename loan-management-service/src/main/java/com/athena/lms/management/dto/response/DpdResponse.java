package com.athena.lms.management.dto.response;

import com.athena.lms.management.enums.LoanStage;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class DpdResponse {
    private UUID loanId;
    private Integer dpd;
    private LoanStage stage;
    private String description;
}
