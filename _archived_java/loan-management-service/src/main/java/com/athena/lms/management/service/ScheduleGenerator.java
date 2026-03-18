package com.athena.lms.management.service;

import com.athena.lms.management.entity.Loan;
import com.athena.lms.management.entity.LoanSchedule;
import com.athena.lms.management.enums.RepaymentFrequency;
import com.athena.lms.management.enums.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates repayment schedules for EMI and FLAT_RATE loan types.
 *
 * EMI formula:  PMT = P * r / (1 - (1+r)^-n)
 *   where r = periodic interest rate, n = number of installments
 *
 * FLAT_RATE formula: totalRepayable = P + P * annualRate * years
 *   installment = totalRepayable / n
 */
@Slf4j
@Component
public class ScheduleGenerator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int SCALE = 2;

    public List<LoanSchedule> generate(Loan loan) {
        int n = numberOfInstallments(loan.getTenorMonths(), loan.getRepaymentFrequency());
        BigDecimal principal = loan.getDisbursedAmount();
        BigDecimal annualRate = loan.getInterestRate().divide(BigDecimal.valueOf(100), MC);

        if (loan.getScheduleType() == ScheduleType.EMI) {
            return generateEmi(loan, principal, annualRate, n);
        } else {
            return generateFlatRate(loan, principal, annualRate, n);
        }
    }

    private List<LoanSchedule> generateEmi(Loan loan, BigDecimal principal, BigDecimal annualRate, int n) {
        BigDecimal periodicRate = periodicRate(annualRate, loan.getRepaymentFrequency());
        BigDecimal emi;

        if (periodicRate.compareTo(BigDecimal.ZERO) == 0) {
            emi = principal.divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);
        } else {
            // PMT = P * r / (1 - (1+r)^-n)
            BigDecimal onePlusR = BigDecimal.ONE.add(periodicRate, MC);
            BigDecimal onePlusRpowN = onePlusR.pow(n, MC);
            BigDecimal denominator = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(onePlusRpowN, MC), MC);
            emi = principal.multiply(periodicRate, MC).divide(denominator, SCALE, RoundingMode.HALF_UP);
        }

        List<LoanSchedule> schedules = new ArrayList<>();
        BigDecimal balance = principal;
        LocalDate dueDate = loan.getFirstRepaymentDate();

        for (int i = 1; i <= n; i++) {
            BigDecimal interestDue = balance.multiply(periodicRate, MC).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal principalDue = (i == n)
                ? balance  // last installment: pay remaining balance
                : emi.subtract(interestDue).setScale(SCALE, RoundingMode.HALF_UP);

            if (principalDue.compareTo(balance) > 0) principalDue = balance;
            BigDecimal totalDue = principalDue.add(interestDue);

            schedules.add(buildInstallment(loan, i, dueDate, principalDue, interestDue, totalDue));
            balance = balance.subtract(principalDue);
            dueDate = nextDueDate(dueDate, loan.getRepaymentFrequency());
        }
        return schedules;
    }

    private List<LoanSchedule> generateFlatRate(Loan loan, BigDecimal principal, BigDecimal annualRate, int n) {
        BigDecimal years = BigDecimal.valueOf(loan.getTenorMonths()).divide(BigDecimal.valueOf(12), MC);
        BigDecimal totalInterest = principal.multiply(annualRate, MC).multiply(years, MC);
        BigDecimal totalRepayable = principal.add(totalInterest);
        BigDecimal installment = totalRepayable.divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);
        BigDecimal principalPerInstallment = principal.divide(BigDecimal.valueOf(n), SCALE, RoundingMode.HALF_UP);
        BigDecimal interestPerInstallment = installment.subtract(principalPerInstallment);

        List<LoanSchedule> schedules = new ArrayList<>();
        LocalDate dueDate = loan.getFirstRepaymentDate();

        for (int i = 1; i <= n; i++) {
            BigDecimal pDue = (i == n)
                ? principal.subtract(principalPerInstallment.multiply(BigDecimal.valueOf(n - 1)))
                : principalPerInstallment;
            BigDecimal totalDue = pDue.add(interestPerInstallment);
            schedules.add(buildInstallment(loan, i, dueDate, pDue, interestPerInstallment, totalDue));
            dueDate = nextDueDate(dueDate, loan.getRepaymentFrequency());
        }
        return schedules;
    }

    private LoanSchedule buildInstallment(Loan loan, int no, LocalDate dueDate,
                                           BigDecimal principalDue, BigDecimal interestDue, BigDecimal totalDue) {
        return LoanSchedule.builder()
            .loan(loan)
            .tenantId(loan.getTenantId())
            .installmentNo(no)
            .dueDate(dueDate)
            .principalDue(principalDue)
            .interestDue(interestDue)
            .feeDue(BigDecimal.ZERO)
            .penaltyDue(BigDecimal.ZERO)
            .totalDue(totalDue)
            .principalPaid(BigDecimal.ZERO)
            .interestPaid(BigDecimal.ZERO)
            .feePaid(BigDecimal.ZERO)
            .penaltyPaid(BigDecimal.ZERO)
            .totalPaid(BigDecimal.ZERO)
            .status("PENDING")
            .build();
    }

    private int numberOfInstallments(int tenorMonths, RepaymentFrequency freq) {
        return switch (freq) {
            case DAILY    -> tenorMonths * 30;
            case WEEKLY   -> tenorMonths * 4;
            case BIWEEKLY -> tenorMonths * 2;
            case MONTHLY  -> tenorMonths;
        };
    }

    private BigDecimal periodicRate(BigDecimal annualRate, RepaymentFrequency freq) {
        int periods = switch (freq) {
            case DAILY    -> 360;
            case WEEKLY   -> 52;
            case BIWEEKLY -> 26;
            case MONTHLY  -> 12;
        };
        return annualRate.divide(BigDecimal.valueOf(periods), MC);
    }

    private LocalDate nextDueDate(LocalDate current, RepaymentFrequency freq) {
        return switch (freq) {
            case DAILY    -> current.plusDays(1);
            case WEEKLY   -> current.plusWeeks(1);
            case BIWEEKLY -> current.plusWeeks(2);
            case MONTHLY  -> current.plusMonths(1);
        };
    }
}
