package com.athena.lms.product.service;

import com.athena.lms.product.dto.request.CreateProductRequest;
import com.athena.lms.product.dto.request.SimulateScheduleRequest;
import com.athena.lms.product.dto.response.ProductResponse;
import com.athena.lms.product.dto.response.ScheduleResponse;
import com.athena.lms.product.entity.Product;
import com.athena.lms.product.entity.ProductFee;
import com.athena.lms.product.entity.ProductTemplate;
import com.athena.lms.product.entity.ProductVersion;
import com.athena.lms.product.enums.*;
import com.athena.lms.product.repository.ProductRepository;
import com.athena.lms.product.repository.ProductTemplateRepository;
import com.athena.lms.product.repository.ProductVersionRepository;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVersionRepository versionRepository;
    private final ProductTemplateRepository templateRepository;
    private final ScheduleSimulator scheduleSimulator;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest req, String tenantId, String createdBy) {
        if (productRepository.existsByProductCodeAndTenantId(req.getProductCode(), tenantId)) {
            throw BusinessException.conflict("Product code already exists: " + req.getProductCode());
        }

        Product product = buildProduct(req, tenantId, createdBy);
        product = productRepository.save(product);
        saveVersionSnapshot(product, createdBy, "Initial creation");
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id, String tenantId) {
        return ProductResponse.from(loadProduct(id, tenantId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listProducts(String tenantId, Pageable pageable) {
        return PageResponse.from(productRepository.findByTenantId(tenantId, pageable)
                .map(ProductResponse::from));
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, CreateProductRequest req, String tenantId,
                                          String changedBy, String changeReason) {
        Product product = loadProduct(id, tenantId);

        // Save snapshot before changes
        saveVersionSnapshot(product, changedBy, changeReason);

        // Apply changes
        applyProductChanges(product, req);
        product.setVersion(product.getVersion() + 1);

        // Two-person auth gate
        if (product.isRequiresTwoPersonAuth() && product.getAuthThresholdAmount() != null
                && req.getMaxAmount() != null
                && req.getMaxAmount().compareTo(product.getAuthThresholdAmount()) > 0) {
            product.setPendingAuthorization(true);
            product.setStatus(ProductStatus.DRAFT);
        }

        product = productRepository.save(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse activateProduct(UUID id, String tenantId, String approvedBy) {
        Product product = loadProduct(id, tenantId);
        product.setStatus(ProductStatus.ACTIVE);
        product.setPendingAuthorization(false);
        log.info("Product {} activated by {}", product.getProductCode(), approvedBy);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse deactivateProduct(UUID id, String tenantId) {
        Product product = loadProduct(id, tenantId);
        product.setStatus(ProductStatus.INACTIVE);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse pauseProduct(UUID id, String tenantId, String pausedBy) {
        Product product = loadProduct(id, tenantId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw BusinessException.conflict(
                "Product must be ACTIVE to pause; current status: " + product.getStatus());
        }
        product.setStatus(ProductStatus.PAUSED);
        log.info("Product {} paused by {}", product.getProductCode(), pausedBy);
        return ProductResponse.from(productRepository.save(product));
    }

    public ScheduleResponse simulateSchedule(UUID id, SimulateScheduleRequest req, String tenantId) {
        // Optionally load product to use its defaults, but req values take precedence
        loadProduct(id, tenantId); // validates product exists and belongs to tenant
        return scheduleSimulator.simulate(req);
    }

    @Transactional(readOnly = true)
    public List<?> getProductVersions(UUID id, String tenantId) {
        loadProduct(id, tenantId);
        return versionRepository.findByProductIdOrderByVersionNumberDesc(id);
    }

    @Transactional
    public ProductResponse createFromTemplate(String templateCode, String tenantId, String createdBy) {
        ProductTemplate template = templateRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new ResourceNotFoundException("Product template", templateCode));

        CreateProductRequest req;
        try {
            req = objectMapper.readValue(template.getConfiguration(), CreateProductRequest.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse template configuration: " + e.getMessage());
        }
        req.setTemplateId(templateCode);
        return createProduct(req, tenantId, createdBy);
    }

    public List<ProductTemplate> listTemplates() {
        return templateRepository.findByIsActiveTrue();
    }

    public ProductTemplate getTemplate(String code) {
        return templateRepository.findByTemplateCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("ProductTemplate", code));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Product loadProduct(UUID id, String tenantId) {
        return productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Product buildProduct(CreateProductRequest req, String tenantId, String createdBy) {
        List<ProductFee> fees = req.getFees().stream()
                .map(f -> ProductFee.builder()
                        .tenantId(tenantId)
                        .feeName(f.getFeeName())
                        .feeType(FeeType.valueOf(f.getFeeType().toUpperCase()))
                        .calculationType(CalculationType.valueOf(f.getCalculationType().toUpperCase()))
                        .amount(f.getAmount())
                        .rate(f.getRate())
                        .isMandatory(f.isMandatory())
                        .build())
                .collect(Collectors.toList());

        Product product = Product.builder()
                .tenantId(tenantId)
                .productCode(req.getProductCode())
                .name(req.getName())
                .productType(ProductType.valueOf(req.getProductType().toUpperCase()))
                .description(req.getDescription())
                .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
                .minAmount(req.getMinAmount())
                .maxAmount(req.getMaxAmount())
                .minTenorDays(req.getMinTenorDays())
                .maxTenorDays(req.getMaxTenorDays())
                .scheduleType(ScheduleType.valueOf(req.getScheduleType().toUpperCase()))
                .repaymentFrequency(RepaymentFrequency.valueOf(req.getRepaymentFrequency().toUpperCase()))
                .nominalRate(req.getNominalRate())
                .penaltyRate(req.getPenaltyRate() != null ? req.getPenaltyRate() : java.math.BigDecimal.ZERO)
                .penaltyGraceDays(req.getPenaltyGraceDays() != null ? req.getPenaltyGraceDays() : 1)
                .gracePeriodDays(req.getGracePeriodDays() != null ? req.getGracePeriodDays() : 0)
                .processingFeeRate(req.getProcessingFeeRate() != null ? req.getProcessingFeeRate() : java.math.BigDecimal.ZERO)
                .processingFeeMin(req.getProcessingFeeMin() != null ? req.getProcessingFeeMin() : java.math.BigDecimal.ZERO)
                .processingFeeMax(req.getProcessingFeeMax())
                .requiresCollateral(req.isRequiresCollateral())
                .minCreditScore(req.getMinCreditScore())
                .maxDtir(req.getMaxDtir())
                .requiresTwoPersonAuth(req.isRequiresTwoPersonAuth())
                .authThresholdAmount(req.getAuthThresholdAmount())
                .pendingAuthorization(req.isRequiresTwoPersonAuth())
                .templateId(req.getTemplateId())
                .createdBy(createdBy)
                .fees(fees)
                .build();
        fees.forEach(f -> f.setProduct(product));
        return product;
    }

    private void applyProductChanges(Product product, CreateProductRequest req) {
        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getMaxAmount() != null) product.setMaxAmount(req.getMaxAmount());
        if (req.getMinAmount() != null) product.setMinAmount(req.getMinAmount());
        if (req.getMaxTenorDays() != null) product.setMaxTenorDays(req.getMaxTenorDays());
        if (req.getMinTenorDays() != null) product.setMinTenorDays(req.getMinTenorDays());
        if (req.getNominalRate() != null) product.setNominalRate(req.getNominalRate());
        if (req.getPenaltyRate() != null) product.setPenaltyRate(req.getPenaltyRate());
        if (req.getScheduleType() != null) product.setScheduleType(ScheduleType.valueOf(req.getScheduleType().toUpperCase()));
        if (req.getRepaymentFrequency() != null) product.setRepaymentFrequency(RepaymentFrequency.valueOf(req.getRepaymentFrequency().toUpperCase()));
    }

    private void saveVersionSnapshot(Product product, String changedBy, String reason) {
        try {
            String snapshot = objectMapper.writeValueAsString(ProductResponse.from(product));
            ProductVersion version = ProductVersion.builder()
                    .productId(product.getId())
                    .versionNumber(product.getVersion())
                    .snapshot(snapshot)
                    .changedBy(changedBy)
                    .changeReason(reason)
                    .build();
            versionRepository.save(version);
        } catch (JsonProcessingException e) {
            log.warn("Failed to save version snapshot for product {}: {}", product.getId(), e.getMessage());
        }
    }
}
