package com.athena.lms.accounting.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PostJournalEntryRequest {
    @NotBlank private String reference;
    private String description;
    private LocalDate entryDate;
    @NotEmpty @Size(min = 2) private List<JournalLineRequest> lines;
}
