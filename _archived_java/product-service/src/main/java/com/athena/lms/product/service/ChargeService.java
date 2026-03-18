package com.athena.lms.product.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.product.dto.request.ChargeTierRequest;
import com.athena.lms.product.dto.request.CreateChargeRequest;
import com.athena.lms.product.dto.response.ChargeCalculationResponse;
import com.athena.lms.product.dto.response.TransactionChargeResponse;
import com.athena.lms.product.entity.ChargeTier;
import com.athena.lms.product.entity.TransactionCharge;
import com.athena.lms.product.enums.ChargeCalculationType;
import com.athena.lms.product.enums.ChargeTransactionType;
import com.athena.lms.product.repository.TransactionChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeService {

    private final TransactionChargeRepository chargeRepository;

    @Transactional
    public TransactionChargeResponse createCharge(CreateChargeRequest req, String tenantId) {
        if (chargeRepository.existsByChargeCodeAndTenantId(req.getChargeCode(), tenantId)) {
            throw BusinessException.badRequest("Charge code already exists: " + req.getChargeCode());
        }

        ChargeTransactionType txnType = parseTransactionType(req.getTransactionType());
        ChargeCalculationType calcType = parseCalculationType(req.getCalculationType());

        TransactionCharge charge = TransactionCharge.builder()
                .tenantId(tenantId)
                .chargeCode(req.getChargeCode())
                .chargeName(req.getChargeName())
                .transactionType(txnType)
                .calculationType(calcType)
                .flatAmount(req.getFlatAmount())
                .percentageRate(req.getPercentageRate())
                .minAmount(req.getMinAmount())
                .maxAmount(req.getMaxAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
                .build();

        if (req.getTiers() != null) {
            for (ChargeTierRequest tierReq : req.getTiers()) {
                ChargeTier tier = ChargeTier.builder()
                        .charge(charge)
                        .fromAmount(tierReq.getFromAmount())
                        .toAmount(tierReq.getToAmount())
                        .flatAmount(tierReq.getFlatAmount())
                        .percentageRate(tierReq.getPercentageRate())
                        .build();
                charge.getTiers().add(tier);
            }
        }

        charge = chargeRepository.save(charge);
        log.info("Created charge config {} ({}) for tenant {}", charge.getChargeCode(), charge.getId(), tenantId);
        return TransactionChargeResponse.from(charge);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionChargeResponse> listCharges(String tenantId, Pageable pageable) {
        return PageResponse.from(chargeRepository.findByTenantId(tenantId, pageable)
                .map(TransactionChargeResponse::from));
    }

    @Transactional(readOnly = true)
    public TransactionChargeResponse getCharge(UUID id, String tenantId) {
        return TransactionChargeResponse.from(
                chargeRepository.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Charge", id)));
    }

    @Transactional
    public TransactionChargeResponse updateCharge(UUID id, CreateChargeRequest req, String tenantId) {
        TransactionCharge charge = chargeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge", id));

        if (req.getChargeName() != null) charge.setChargeName(req.getChargeName());
        if (req.getTransactionType() != null) charge.setTransactionType(parseTransactionType(req.getTransactionType()));
        if (req.getCalculationType() != null) charge.setCalculationType(parseCalculationType(req.getCalculationType()));
        if (req.getFlatAmount() != null) charge.setFlatAmount(req.getFlatAmount());
        if (req.getPercentageRate() != null) charge.setPercentageRate(req.getPercentageRate());
        if (req.getMinAmount() != null) charge.setMinAmount(req.getMinAmount());
        if (req.getMaxAmount() != null) charge.setMaxAmount(req.getMaxAmount());
        if (req.getCurrency() != null) charge.setCurrency(req.getCurrency());

        if (req.getTiers() != null) {
            charge.getTiers().clear();
            for (ChargeTierRequest tierReq : req.getTiers()) {
                ChargeTier tier = ChargeTier.builder()
                        .charge(charge)
                        .fromAmount(tierReq.getFromAmount())
                        .toAmount(tierReq.getToAmount())
                        .flatAmount(tierReq.getFlatAmount())
                        .percentageRate(tierReq.getPercentageRate())
                        .build();
                charge.getTiers().add(tier);
            }
        }

        charge = chargeRepository.save(charge);
        return TransactionChargeResponse.from(charge);
    }

    @Transactional
    public void deleteCharge(UUID id, String tenantId) {
        TransactionCharge charge = chargeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge", id));
        chargeRepository.delete(charge);
    }

    @Transactional(readOnly = true)
    public ChargeCalculationResponse calculateCharge(String transactionType, BigDecimal amount, String tenantId) {
        ChargeTransactionType txnType = parseTransactionType(transactionType);
        List<TransactionCharge> charges = chargeRepository
                .findByTenantIdAndTransactionTypeAndIsActiveTrue(tenantId, txnType);

        if (charges.isEmpty()) {
            return ChargeCalculationResponse.builder()
                    .transactionType(transactionType)
                    .transactionAmount(amount)
                    .chargeAmount(BigDecimal.ZERO)
                    .currency("KES")
                    .build();
        }

        TransactionCharge charge = charges.get(0);
        BigDecimal chargeAmount = doCalculate(charge, amount);

        return ChargeCalculationResponse.builder()
                .chargeCode(charge.getChargeCode())
                .chargeName(charge.getChargeName())
                .transactionType(transactionType)
                .transactionAmount(amount)
                .chargeAmount(chargeAmount)
                .currency(charge.getCurrency())
                .build();
    }

    private BigDecimal doCalculate(TransactionCharge charge, BigDecimal amount) {
        return switch (charge.getCalculationType()) {
            case FLAT -> charge.getFlatAmount() != null ? charge.getFlatAmount() : BigDecimal.ZERO;
            case PERCENTAGE -> {
                if (charge.getPercentageRate() == null) yield BigDecimal.ZERO;
                BigDecimal calculated = amount.multiply(charge.getPercentageRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (charge.getMinAmount() != null && calculated.compareTo(charge.getMinAmount()) < 0) {
                    calculated = charge.getMinAmount();
                }
                if (charge.getMaxAmount() != null && calculated.compareTo(charge.getMaxAmount()) > 0) {
                    calculated = charge.getMaxAmount();
                }
                yield calculated;
            }
            case TIERED -> {
                for (ChargeTier tier : charge.getTiers()) {
                    if (amount.compareTo(tier.getFromAmount()) >= 0
                            && amount.compareTo(tier.getToAmount()) <= 0) {
                        if (tier.getFlatAmount() != null) yield tier.getFlatAmount();
                        if (tier.getPercentageRate() != null) {
                            yield amount.multiply(tier.getPercentageRate())
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                        }
                    }
                }
                yield BigDecimal.ZERO;
            }
        };
    }

    private ChargeTransactionType parseTransactionType(String type) {
        try {
            return ChargeTransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid transaction type: " + type);
        }
    }

    private ChargeCalculationType parseCalculationType(String type) {
        try {
            return ChargeCalculationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid calculation type: " + type);
        }
    }
}
