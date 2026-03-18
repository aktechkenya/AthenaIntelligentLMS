package com.athena.lms.fraud.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCaseNoteRequest {
    @NotBlank
    private String content;
    @NotBlank
    private String author;
    private String noteType;
}
