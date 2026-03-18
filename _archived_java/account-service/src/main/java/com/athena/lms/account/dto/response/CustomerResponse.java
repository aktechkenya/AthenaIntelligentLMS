package com.athena.lms.account.dto.response;

import com.athena.lms.account.entity.Customer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {

    private UUID id;
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String nationalId;
    private String gender;
    private String address;
    private String customerType;
    private String status;
    private String kycStatus;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CustomerResponse from(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .customerId(c.getCustomerId())
                .firstName(c.getFirstName())
                .lastName(c.getLastName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .dateOfBirth(c.getDateOfBirth())
                .nationalId(c.getNationalId())
                .gender(c.getGender())
                .address(c.getAddress())
                .customerType(c.getCustomerType().name())
                .status(c.getStatus().name())
                .kycStatus(c.getKycStatus())
                .source(c.getSource())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
