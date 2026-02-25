package com.athena.lms.payment.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.payment.dto.request.*;
import com.athena.lms.payment.dto.response.PaymentMethodResponse;
import com.athena.lms.payment.dto.response.PaymentResponse;
import com.athena.lms.payment.entity.Payment;
import com.athena.lms.payment.entity.PaymentMethod;
import com.athena.lms.payment.enums.PaymentStatus;
import com.athena.lms.payment.enums.PaymentType;
import com.athena.lms.payment.event.PaymentEventPublisher;
import com.athena.lms.payment.repository.PaymentMethodRepository;
import com.athena.lms.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final PaymentMethodRepository methodRepo;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public PaymentResponse initiate(InitiatePaymentRequest req, String tenantId, String userId) {
        Payment payment = Payment.builder()
            .tenantId(tenantId)
            .customerId(req.getCustomerId())
            .loanId(req.getLoanId())
            .applicationId(req.getApplicationId())
            .paymentType(req.getPaymentType())
            .paymentChannel(req.getPaymentChannel())
            .status(PaymentStatus.PENDING)
            .amount(req.getAmount())
            .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
            .externalReference(req.getExternalReference())
            .internalReference(UUID.randomUUID().toString())
            .description(req.getDescription())
            .paymentMethodId(req.getPaymentMethodId())
            .initiatedAt(OffsetDateTime.now())
            .createdBy(userId)
            .build();

        payment = paymentRepo.save(payment);
        eventPublisher.publishInitiated(payment);
        return toResponse(payment);
    }

    public PaymentResponse getById(UUID id, String tenantId) {
        return toResponse(findPayment(id, tenantId));
    }

    public PageResponse<PaymentResponse> list(String tenantId, PaymentStatus status, PaymentType type, Pageable pageable) {
        Page<Payment> page;
        if (status != null) {
            page = paymentRepo.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (type != null) {
            page = paymentRepo.findByTenantIdAndPaymentType(tenantId, type, pageable);
        } else {
            page = paymentRepo.findByTenantId(tenantId, pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    public List<PaymentResponse> listByCustomer(UUID customerId, String tenantId) {
        return paymentRepo.findByTenantIdAndCustomerId(tenantId, customerId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PaymentResponse getByReference(String reference) {
        return paymentRepo.findByExternalReference(reference)
            .or(() -> paymentRepo.findByInternalReference(reference))
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", reference));
    }

    @Transactional
    public PaymentResponse process(UUID id, String tenantId) {
        Payment payment = findPaymentWithStatus(id, tenantId, PaymentStatus.PENDING);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProcessedAt(OffsetDateTime.now());
        return toResponse(paymentRepo.save(payment));
    }

    @Transactional
    public PaymentResponse complete(UUID id, CompletePaymentRequest req, String tenantId) {
        Payment payment = findPayment(id, tenantId);
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new BusinessException("Payment must be PENDING or PROCESSING to complete");
        }
        if (req.getExternalReference() != null) payment.setExternalReference(req.getExternalReference());
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(OffsetDateTime.now());
        paymentRepo.save(payment);
        eventPublisher.publishCompleted(payment);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse fail(UUID id, FailPaymentRequest req, String tenantId) {
        Payment payment = findPayment(id, tenantId);
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REVERSED) {
            throw new BusinessException("Cannot fail a payment in status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(req.getReason());
        paymentRepo.save(payment);
        eventPublisher.publishFailed(payment);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse reverse(UUID id, ReversePaymentRequest req, String tenantId) {
        Payment payment = findPaymentWithStatus(id, tenantId, PaymentStatus.COMPLETED);
        payment.setStatus(PaymentStatus.REVERSED);
        payment.setReversalReason(req.getReason());
        payment.setReversedAt(OffsetDateTime.now());
        paymentRepo.save(payment);
        eventPublisher.publishReversed(payment);
        return toResponse(payment);
    }

    @Transactional
    public PaymentMethodResponse addPaymentMethod(AddPaymentMethodRequest req, String tenantId) {
        // If new method is default, clear existing default
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            methodRepo.findByTenantIdAndCustomerIdAndIsActiveTrue(tenantId, req.getCustomerId())
                .forEach(m -> { m.setIsDefault(false); methodRepo.save(m); });
        }
        PaymentMethod method = PaymentMethod.builder()
            .tenantId(tenantId)
            .customerId(req.getCustomerId())
            .methodType(req.getMethodType())
            .accountNumber(req.getAccountNumber())
            .accountName(req.getAccountName())
            .alias(req.getAlias())
            .provider(req.getProvider())
            .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
            .isActive(true)
            .build();
        return toMethodResponse(methodRepo.save(method));
    }

    public List<PaymentMethodResponse> getPaymentMethods(UUID customerId, String tenantId) {
        return methodRepo.findByTenantIdAndCustomerIdAndIsActiveTrue(tenantId, customerId)
            .stream().map(this::toMethodResponse).collect(Collectors.toList());
    }

    private Payment findPayment(UUID id, String tenantId) {
        return paymentRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", id.toString()));
    }

    private Payment findPaymentWithStatus(UUID id, String tenantId, PaymentStatus expected) {
        Payment p = findPayment(id, tenantId);
        if (p.getStatus() != expected) {
            throw new BusinessException("Payment must be " + expected + ", current: " + p.getStatus());
        }
        return p;
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
            .id(p.getId()).tenantId(p.getTenantId()).customerId(p.getCustomerId())
            .loanId(p.getLoanId()).applicationId(p.getApplicationId())
            .paymentType(p.getPaymentType()).paymentChannel(p.getPaymentChannel())
            .status(p.getStatus()).amount(p.getAmount()).currency(p.getCurrency())
            .externalReference(p.getExternalReference()).internalReference(p.getInternalReference())
            .description(p.getDescription()).failureReason(p.getFailureReason())
            .reversalReason(p.getReversalReason()).initiatedAt(p.getInitiatedAt())
            .processedAt(p.getProcessedAt()).completedAt(p.getCompletedAt())
            .reversedAt(p.getReversedAt()).createdAt(p.getCreatedAt())
            .build();
    }

    private PaymentMethodResponse toMethodResponse(PaymentMethod m) {
        return PaymentMethodResponse.builder()
            .id(m.getId()).customerId(m.getCustomerId()).methodType(m.getMethodType())
            .alias(m.getAlias()).accountNumber(m.getAccountNumber()).accountName(m.getAccountName())
            .provider(m.getProvider()).isDefault(m.getIsDefault()).isActive(m.getIsActive())
            .createdAt(m.getCreatedAt())
            .build();
    }
}
