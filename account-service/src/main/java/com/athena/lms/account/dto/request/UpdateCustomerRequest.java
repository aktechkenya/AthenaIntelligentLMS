package com.athena.lms.account.dto.request;

import lombok.Data;

@Data
public class UpdateCustomerRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String dateOfBirth;
    private String nationalId;
    private String gender;
    private String address;
    private String customerType;
}
