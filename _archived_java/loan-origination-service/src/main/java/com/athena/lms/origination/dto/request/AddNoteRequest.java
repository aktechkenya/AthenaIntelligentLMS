package com.athena.lms.origination.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddNoteRequest {
    @NotBlank private String content;
    private String noteType = "UNDERWRITER";
}
