package com.athena.lms.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCustomerRequest {

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "firstName is required")
    private String firstName;

    @NotBlank(message = "lastName is required")
    private String lastName;

    private String email;
    private String phone;
    private String dateOfBirth;
    private String nationalId;
    private String gender;
    private String address;
    private String customerType;  // INDIVIDUAL | BUSINESS
    private String source;        // BRANCH | MOBILE | API
}
