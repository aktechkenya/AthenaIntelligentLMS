package com.athena.lms.product.seeder;

import com.athena.lms.product.dto.request.CreateProductRequest;
import com.athena.lms.product.entity.ProductTemplate;
import com.athena.lms.product.enums.ProductType;
import com.athena.lms.product.repository.ProductTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds 5 product templates on application startup if they don't exist.
 * Templates: TPL-NL-001, TPL-PL-001, TPL-BNPL-001, TPL-SME-001, TPL-GRP-001
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDataSeeder implements ApplicationRunner {

    private final ProductTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (templateRepository.count() > 0) {
            log.info("Product templates already seeded, skipping.");
            return;
        }

        log.info("Seeding 5 product templates...");
        List<ProductTemplate> templates = List.of(
                buildTemplate("TPL-NL-001", "Nano Loan", ProductType.NANO_LOAN, buildNanoLoan()),
                buildTemplate("TPL-PL-001", "Personal Loan", ProductType.PERSONAL_LOAN, buildPersonalLoan()),
                buildTemplate("TPL-BNPL-001", "Buy Now Pay Later", ProductType.BNPL, buildBnpl()),
                buildTemplate("TPL-SME-001", "SME Business Loan", ProductType.SME_LOAN, buildSmeLoan()),
                buildTemplate("TPL-GRP-001", "Group Loan", ProductType.GROUP_LOAN, buildGroupLoan())
        );
        templateRepository.saveAll(templates);
        log.info("Seeded {} product templates successfully.", templates.size());
    }

    private ProductTemplate buildTemplate(String code, String name, ProductType type,
                                           CreateProductRequest config) throws Exception {
        return ProductTemplate.builder()
                .templateCode(code)
                .name(name)
                .productType(type)
                .configuration(objectMapper.writeValueAsString(config))
                .isActive(true)
                .build();
    }

    private CreateProductRequest buildNanoLoan() {
        CreateProductRequest req = new CreateProductRequest();
        req.setProductCode("NL-" + System.currentTimeMillis() % 10000);
        req.setName("Nano Loan");
        req.setProductType("NANO_LOAN");
        req.setDescription("Quick micro-credit for small urgent needs");
        req.setCurrency("KES");
        req.setMinAmount(new BigDecimal("500"));
        req.setMaxAmount(new BigDecimal("10000"));
        req.setMinTenorDays(7);
        req.setMaxTenorDays(30);
        req.setScheduleType("EMI");
        req.setRepaymentFrequency("MONTHLY");
        req.setNominalRate(new BigDecimal("15.0"));
        req.setPenaltyRate(new BigDecimal("5.0"));
        req.setPenaltyGraceDays(1);
        req.setProcessingFeeRate(new BigDecimal("2.0"));
        req.setFees(List.of(feeReq("Processing Fee", "UPFRONT", "PERCENTAGE", null, new BigDecimal("2.0"))));
        return req;
    }

    private CreateProductRequest buildPersonalLoan() {
        CreateProductRequest req = new CreateProductRequest();
        req.setProductCode("PL-" + System.currentTimeMillis() % 10000);
        req.setName("Personal Loan");
        req.setProductType("PERSONAL_LOAN");
        req.setDescription("Personal loan for individuals — reducing balance EMI");
        req.setCurrency("KES");
        req.setMinAmount(new BigDecimal("10000"));
        req.setMaxAmount(new BigDecimal("500000"));
        req.setMinTenorDays(90);
        req.setMaxTenorDays(365);
        req.setScheduleType("EMI");
        req.setRepaymentFrequency("MONTHLY");
        req.setNominalRate(new BigDecimal("18.0"));
        req.setPenaltyRate(new BigDecimal("5.0"));
        req.setProcessingFeeRate(new BigDecimal("3.0"));
        req.setFees(List.of(
                feeReq("Processing Fee", "UPFRONT", "PERCENTAGE", null, new BigDecimal("3.0")),
                feeReq("Insurance Premium", "MONTHLY", "PERCENTAGE", null, new BigDecimal("0.5"))
        ));
        return req;
    }

    private CreateProductRequest buildBnpl() {
        CreateProductRequest req = new CreateProductRequest();
        req.setProductCode("BNPL-" + System.currentTimeMillis() % 10000);
        req.setName("Buy Now Pay Later");
        req.setProductType("BNPL");
        req.setDescription("0% interest promotional BNPL — flat fee model");
        req.setCurrency("KES");
        req.setMinAmount(new BigDecimal("1000"));
        req.setMaxAmount(new BigDecimal("50000"));
        req.setMinTenorDays(30);
        req.setMaxTenorDays(90);
        req.setScheduleType("FLAT");
        req.setRepaymentFrequency("MONTHLY");
        req.setNominalRate(BigDecimal.ZERO);   // 0% promo
        req.setPenaltyRate(new BigDecimal("3.0"));
        req.setFees(List.of(
                feeReq("Merchant Fee", "UPFRONT", "PERCENTAGE", null, new BigDecimal("1.5"))
        ));
        return req;
    }

    private CreateProductRequest buildSmeLoan() {
        CreateProductRequest req = new CreateProductRequest();
        req.setProductCode("SME-" + System.currentTimeMillis() % 10000);
        req.setName("SME Business Loan");
        req.setProductType("SME_LOAN");
        req.setDescription("Working capital and asset finance for SMEs");
        req.setCurrency("KES");
        req.setMinAmount(new BigDecimal("100000"));
        req.setMaxAmount(new BigDecimal("5000000"));
        req.setMinTenorDays(180);
        req.setMaxTenorDays(1095);
        req.setScheduleType("EMI");
        req.setRepaymentFrequency("MONTHLY");
        req.setNominalRate(new BigDecimal("14.0"));
        req.setPenaltyRate(new BigDecimal("4.0"));
        req.setProcessingFeeRate(new BigDecimal("2.0"));
        req.setProcessingFeeMax(new BigDecimal("50000"));
        req.setRequiresCollateral(true);
        req.setMinCreditScore(600);
        req.setFees(List.of(
                feeReq("Processing Fee", "UPFRONT", "PERCENTAGE", null, new BigDecimal("2.0")),
                feeReq("Legal Fee", "UPFRONT", "FLAT", new BigDecimal("5000"), null)
        ));
        return req;
    }

    private CreateProductRequest buildGroupLoan() {
        CreateProductRequest req = new CreateProductRequest();
        req.setProductCode("GRP-" + System.currentTimeMillis() % 10000);
        req.setName("Group Loan");
        req.setProductType("GROUP_LOAN");
        req.setDescription("Group lending with graduated repayment schedule");
        req.setCurrency("KES");
        req.setMinAmount(new BigDecimal("5000"));
        req.setMaxAmount(new BigDecimal("100000"));
        req.setMinTenorDays(90);
        req.setMaxTenorDays(365);
        req.setScheduleType("GRADUATED");
        req.setRepaymentFrequency("MONTHLY");
        req.setNominalRate(new BigDecimal("12.0"));
        req.setPenaltyRate(new BigDecimal("3.0"));
        req.setFees(List.of(
                feeReq("Group Registration Fee", "UPFRONT", "FLAT", new BigDecimal("500"), null)
        ));
        return req;
    }

    private CreateProductRequest.FeeRequest feeReq(
            String name, String type, String calcType, BigDecimal amount, BigDecimal rate) {
        CreateProductRequest.FeeRequest f = new CreateProductRequest.FeeRequest();
        f.setFeeName(name);
        f.setFeeType(type);
        f.setCalculationType(calcType);
        f.setAmount(amount);
        f.setRate(rate);
        f.setMandatory(true);
        return f;
    }
}
