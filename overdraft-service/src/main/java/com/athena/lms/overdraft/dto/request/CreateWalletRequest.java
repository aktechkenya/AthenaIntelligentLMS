package com.athena.lms.overdraft.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWalletRequest {
    @NotBlank
    private String customerId;
    private String currency = "KES";
}
