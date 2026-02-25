package com.athena.lms.origination.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectApplicationRequest {
    @NotBlank private String reason;
}
