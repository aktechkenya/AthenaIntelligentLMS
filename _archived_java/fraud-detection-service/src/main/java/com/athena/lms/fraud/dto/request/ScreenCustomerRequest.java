package com.athena.lms.fraud.dto.request;

import lombok.Data;

@Data
public class ScreenCustomerRequest {
    private String customerId;
    private String name;
    private String nationalId;
    private String phone;
}
