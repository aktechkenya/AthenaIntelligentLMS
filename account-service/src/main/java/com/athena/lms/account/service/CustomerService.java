package com.athena.lms.account.service;

import com.athena.lms.account.dto.request.CreateCustomerRequest;
import com.athena.lms.account.dto.request.UpdateCustomerRequest;
import com.athena.lms.account.dto.response.CustomerResponse;
import com.athena.lms.account.entity.Customer;
import com.athena.lms.account.event.AccountEventPublisher;
import com.athena.lms.account.repository.CustomerRepository;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AccountEventPublisher eventPublisher;

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest req, String tenantId) {
        if (customerRepository.existsByCustomerIdAndTenantId(req.getCustomerId(), tenantId)) {
            throw BusinessException.badRequest("Customer ID already exists: " + req.getCustomerId());
        }

        Customer.CustomerType type = Customer.CustomerType.INDIVIDUAL;
        if (req.getCustomerType() != null) {
            try {
                type = Customer.CustomerType.valueOf(req.getCustomerType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("Invalid customer type: " + req.getCustomerType());
            }
        }

        Customer customer = Customer.builder()
                .tenantId(tenantId)
                .customerId(req.getCustomerId())
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .dateOfBirth(parseDate(req.getDateOfBirth()))
                .nationalId(req.getNationalId())
                .gender(req.getGender())
                .address(req.getAddress())
                .customerType(type)
                .source(req.getSource() != null ? req.getSource() : "BRANCH")
                .build();

        customer = customerRepository.save(customer);
        eventPublisher.publishCustomerCreated(customer.getId(), customer.getCustomerId(), tenantId);
        log.info("Created customer {} ({}) in tenant {}", customer.getCustomerId(), customer.getId(), tenantId);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID id, String tenantId) {
        Customer customer = customerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> listCustomers(String tenantId, Pageable pageable) {
        return PageResponse.from(customerRepository.findByTenantId(tenantId, pageable)
                .map(CustomerResponse::from));
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest req, String tenantId) {
        Customer customer = customerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        if (req.getFirstName() != null) customer.setFirstName(req.getFirstName());
        if (req.getLastName() != null) customer.setLastName(req.getLastName());
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getPhone() != null) customer.setPhone(req.getPhone());
        if (req.getDateOfBirth() != null) customer.setDateOfBirth(parseDate(req.getDateOfBirth()));
        if (req.getNationalId() != null) customer.setNationalId(req.getNationalId());
        if (req.getGender() != null) customer.setGender(req.getGender());
        if (req.getAddress() != null) customer.setAddress(req.getAddress());
        if (req.getCustomerType() != null) {
            try {
                customer.setCustomerType(Customer.CustomerType.valueOf(req.getCustomerType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("Invalid customer type: " + req.getCustomerType());
            }
        }

        customer = customerRepository.save(customer);
        eventPublisher.publishCustomerUpdated(customer.getId(), customer.getCustomerId(), tenantId);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse updateStatus(UUID id, String status, String tenantId) {
        Customer customer = customerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        try {
            customer.setStatus(Customer.CustomerStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid customer status: " + status);
        }
        customer = customerRepository.save(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String q, String tenantId) {
        return customerRepository.searchByTenantAndQuery(tenantId, q)
                .stream().map(CustomerResponse::from).collect(Collectors.toList());
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr);
    }
}
