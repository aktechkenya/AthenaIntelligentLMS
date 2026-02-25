package com.athena.lms.product.service;

import com.athena.lms.product.dto.request.SimulateScheduleRequest;
import com.athena.lms.product.dto.response.InstallmentResponse;
import com.athena.lms.product.dto.response.ScheduleResponse;
import com.athena.lms.product.enums.RepaymentFrequency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Core loan schedule simulation engine supporting all 7 schedule types.
 * All monetary values rounded to 2 decimal places. Date arithmetic is exact.
 */
@Service
public class ScheduleSimulator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    private static final int SCALE = 2;

    public ScheduleResponse simulate(SimulateScheduleRequest req) {
        return switch (req.getScheduleType()) {
            case EMI        -> simulateEmi(req);
            case FLAT, FLAT_RATE -> simulateFlat(req);
            case ACTUARIAL  -> simulateActuarial(req);
            case DAILY_SIMPLE -> simulateDailySimple(req);
            case BALLOON    -> simulateBalloon(req);
            case SEASONAL   -> simulateSeasonal(req);
            case GRADUATED  -> simulateGraduated(req);
        };
    }

    // ─── 1. EMI — Reducing Balance ───────────────────────────────────────────

    private ScheduleResponse simulateEmi(SimulateScheduleRequest req) {
        BigDecimal P = req.getPrincipal();
        int periods = computePeriods(req);
        if (periods == 0) periods = 1;

        BigDecimal annualRate = req.getNominalRate().divide(BigDecimal.valueOf(100), MC);
        BigDecimal periodRate = annualRate.divide(BigDecimal.valueOf(periodsPerYear(req.getRepaymentFrequency())), MC);

        List<InstallmentResponse> installments = new ArrayList<>();
        BigDecimal outstanding = P;
        BigDecimal totalInterest = BigDecimal.ZERO;
        LocalDate dueDate = req.getDisbursementDate();

        BigDecimal emi;
        if (periodRate.compareTo(BigDecimal.ZERO) == 0) {
            // Zero interest (BNPL promo)
            emi = P.divide(BigDecimal.valueOf(periods), MC).setScale(SCALE, ROUND);
        } else {
            // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusR = BigDecimal.ONE.add(periodRate);
            BigDecimal onePlusRn = onePlusR.pow(periods, MC);
            emi = P.multiply(periodRate, MC).multiply(onePlusRn, MC)
                    .divide(onePlusRn.subtract(BigDecimal.ONE), MC)
                    .setScale(SCALE, ROUND);
        }

        for (int i = 1; i <= periods; i++) {
            dueDate = dueDate.plusDays(req.getRepaymentFrequency().getDaysInPeriod() > 0
                    ? req.getRepaymentFrequency().getDaysInPeriod() : req.getTenorDays());

            BigDecimal interest = outstanding.multiply(periodRate, MC).setScale(SCALE, ROUND);
            BigDecimal principal = (i == periods)
                    ? outstanding  // Last period: settle remaining
                    : emi.subtract(interest).setScale(SCALE, ROUND);

            if (principal.compareTo(outstanding) > 0) principal = outstanding;
            outstanding = outstanding.subtract(principal).setScale(SCALE, ROUND);
            totalInterest = totalInterest.add(interest);

            installments.add(InstallmentResponse.builder()
                    .installmentNumber(i)
                    .dueDate(dueDate)
                    .principal(principal)
                    .interest(interest)
                    .totalPayment(principal.add(interest))
                    .outstandingBalance(outstanding)
                    .build());
        }

        BigDecimal totalPayable = P.add(totalInterest).setScale(SCALE, ROUND);
        BigDecimal effectiveRate = P.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : totalInterest.divide(P, MC).multiply(BigDecimal.valueOf(100)).setScale(4, ROUND);

        return ScheduleResponse.builder()
                .scheduleType("EMI")
                .principal(P)
                .totalInterest(totalInterest.setScale(SCALE, ROUND))
                .totalPayable(totalPayable)
                .effectiveRate(effectiveRate)
                .numberOfInstallments(periods)
                .installments(installments)
                .build();
    }

    // ─── 2. FLAT — Flat interest on original principal ────────────────────────

    private ScheduleResponse simulateFlat(SimulateScheduleRequest req) {
        BigDecimal P = req.getPrincipal();
        int periods = computePeriods(req);
        if (periods == 0) periods = 1;

        BigDecimal annualRate = req.getNominalRate().divide(BigDecimal.valueOf(100), MC);
        BigDecimal totalInterest = P.multiply(annualRate, MC)
                .multiply(BigDecimal.valueOf(req.getTenorDays()), MC)
                .divide(BigDecimal.valueOf(365), MC)
                .setScale(SCALE, ROUND);

        BigDecimal principalPerPeriod = P.divide(BigDecimal.valueOf(periods), MC).setScale(SCALE, ROUND);
        BigDecimal interestPerPeriod = totalInterest.divide(BigDecimal.valueOf(periods), MC).setScale(SCALE, ROUND);

        List<InstallmentResponse> installments = new ArrayList<>();
        LocalDate dueDate = req.getDisbursementDate();
        BigDecimal outstanding = P;

        for (int i = 1; i <= periods; i++) {
            dueDate = dueDate.plusDays(req.getRepaymentFrequency().getDaysInPeriod() > 0
                    ? req.getRepaymentFrequency().getDaysInPeriod() : req.getTenorDays() / periods);
            BigDecimal principal = (i == periods) ? outstanding : principalPerPeriod;
            outstanding = outstanding.subtract(principal).setScale(SCALE, ROUND);

            installments.add(InstallmentResponse.builder()
                    .installmentNumber(i).dueDate(dueDate)
                    .principal(principal).interest(interestPerPeriod)
                    .totalPayment(principal.add(interestPerPeriod))
                    .outstandingBalance(outstanding).build());
        }

        return ScheduleResponse.builder()
                .scheduleType("FLAT").principal(P)
                .totalInterest(totalInterest).totalPayable(P.add(totalInterest))
                .effectiveRate(req.getNominalRate())
                .numberOfInstallments(periods).installments(installments).build();
    }

    // ─── 3. ACTUARIAL — Same as EMI (reducing balance at period rate) ─────────

    private ScheduleResponse simulateActuarial(SimulateScheduleRequest req) {
        // Actuarial method == EMI reducing balance; override type label
        ScheduleResponse r = simulateEmi(req);
        return ScheduleResponse.builder()
                .scheduleType("ACTUARIAL").principal(r.getPrincipal())
                .totalInterest(r.getTotalInterest()).totalPayable(r.getTotalPayable())
                .effectiveRate(r.getEffectiveRate())
                .numberOfInstallments(r.getNumberOfInstallments())
                .installments(r.getInstallments()).build();
    }

    // ─── 4. DAILY_SIMPLE — Simple daily interest, equal principal per period ──

    private ScheduleResponse simulateDailySimple(SimulateScheduleRequest req) {
        BigDecimal P = req.getPrincipal();
        int periods = computePeriods(req);
        if (periods == 0) periods = 1;
        int daysPerPeriod = req.getRepaymentFrequency().getDaysInPeriod() > 0
                ? req.getRepaymentFrequency().getDaysInPeriod()
                : req.getTenorDays() / periods;

        BigDecimal dailyRate = req.getNominalRate()
                .divide(BigDecimal.valueOf(100), MC)
                .divide(BigDecimal.valueOf(365), MC);

        BigDecimal principalPerPeriod = P.divide(BigDecimal.valueOf(periods), MC).setScale(SCALE, ROUND);
        BigDecimal outstanding = P;
        BigDecimal totalInterest = BigDecimal.ZERO;
        List<InstallmentResponse> installments = new ArrayList<>();
        LocalDate dueDate = req.getDisbursementDate();

        for (int i = 1; i <= periods; i++) {
            dueDate = dueDate.plusDays(daysPerPeriod);
            BigDecimal interest = outstanding.multiply(dailyRate, MC)
                    .multiply(BigDecimal.valueOf(daysPerPeriod), MC).setScale(SCALE, ROUND);
            BigDecimal principal = (i == periods) ? outstanding : principalPerPeriod;
            outstanding = outstanding.subtract(principal).setScale(SCALE, ROUND);
            totalInterest = totalInterest.add(interest);

            installments.add(InstallmentResponse.builder()
                    .installmentNumber(i).dueDate(dueDate)
                    .principal(principal).interest(interest)
                    .totalPayment(principal.add(interest))
                    .outstandingBalance(outstanding).build());
        }

        return ScheduleResponse.builder()
                .scheduleType("DAILY_SIMPLE").principal(P)
                .totalInterest(totalInterest.setScale(SCALE, ROUND))
                .totalPayable(P.add(totalInterest).setScale(SCALE, ROUND))
                .effectiveRate(req.getNominalRate())
                .numberOfInstallments(periods).installments(installments).build();
    }

    // ─── 5. BALLOON — Interest-only installments + principal at end ───────────

    private ScheduleResponse simulateBalloon(SimulateScheduleRequest req) {
        BigDecimal P = req.getPrincipal();
        int periods = computePeriods(req);
        if (periods == 0) periods = 1;

        BigDecimal annualRate = req.getNominalRate().divide(BigDecimal.valueOf(100), MC);
        BigDecimal periodRate = annualRate.divide(BigDecimal.valueOf(periodsPerYear(req.getRepaymentFrequency())), MC);
        BigDecimal periodInterest = P.multiply(periodRate, MC).setScale(SCALE, ROUND);

        List<InstallmentResponse> installments = new ArrayList<>();
        LocalDate dueDate = req.getDisbursementDate();
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (int i = 1; i <= periods; i++) {
            dueDate = dueDate.plusDays(req.getRepaymentFrequency().getDaysInPeriod() > 0
                    ? req.getRepaymentFrequency().getDaysInPeriod() : req.getTenorDays() / periods);
            boolean isLast = (i == periods);
            BigDecimal principal = isLast ? P : BigDecimal.ZERO;
            BigDecimal total = principal.add(periodInterest);
            totalInterest = totalInterest.add(periodInterest);

            installments.add(InstallmentResponse.builder()
                    .installmentNumber(i).dueDate(dueDate)
                    .principal(principal).interest(periodInterest)
                    .totalPayment(total).outstandingBalance(isLast ? BigDecimal.ZERO : P).build());
        }

        return ScheduleResponse.builder()
                .scheduleType("BALLOON").principal(P)
                .totalInterest(totalInterest.setScale(SCALE, ROUND))
                .totalPayable(P.add(totalInterest).setScale(SCALE, ROUND))
                .effectiveRate(req.getNominalRate())
                .numberOfInstallments(periods).installments(installments).build();
    }

    // ─── 6. SEASONAL — Payments only in months 1,3,5,7,9,11 ─────────────────

    private ScheduleResponse simulateSeasonal(SimulateScheduleRequest req) {
        BigDecimal P = req.getPrincipal();
        int tenorMonths = (int) Math.ceil(req.getTenorDays() / 30.0);
        if (tenorMonths < 2) tenorMonths = 2;

        // Payment months: odd months only (harvest months)
        List<Integer> paymentMonths = new ArrayList<>();
        for (int m = 1; m <= tenorMonths; m++) {
            if (m % 2 != 0) paymentMonths.add(m);
        }
        int n = paymentMonths.size();
        if (n == 0) n = 1;

        BigDecimal annualRate = req.getNominalRate().divide(BigDecimal.valueOf(100), MC);
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), MC);
        BigDecimal totalInterest = P.multiply(annualRate, MC)
                .multiply(BigDecimal.valueOf(req.getTenorDays()), MC)
                .divide(BigDecimal.valueOf(365), MC).setScale(SCALE, ROUND);

        BigDecimal paymentAmount = P.add(totalInterest)
                .divide(BigDecimal.valueOf(n), MC).setScale(SCALE, ROUND);

        List<InstallmentResponse> installments = new ArrayList<>();
        BigDecimal outstanding = P;
        BigDecimal outstandingInterest = totalInterest;
        int installNum = 0;

        for (int m = 1; m <= tenorMonths; m++) {
            LocalDate dueDate = req.getDisbursementDate().plusMonths(m);
            boolean isPaymentMonth = (m % 2 != 0);
            installNum++;

            BigDecimal principal = BigDecimal.ZERO;
            BigDecimal interest = BigDecimal.ZERO;

            if (isPaymentMonth) {
                interest = paymentAmount.min(outstandingInterest).setScale(SCALE, ROUND);
                principal = paymentAmount.subtract(interest).setScale(SCALE, ROUND);
                if (principal.compareTo(outstanding) > 0) principal = outstanding;
                outstanding = outstanding.subtract(principal).setScale(SCALE, ROUND);
                outstandingInterest = outstandingInterest.subtract(interest).setScale(SCALE, ROUND);
            }

            installments.add(InstallmentResponse.builder()
                    .installmentNumber(installNum).dueDate(dueDate)
                    .principal(principal).interest(interest)
                    .totalPayment(principal.add(interest))
                    .outstandingBalance(outstanding).build());
        }

        return ScheduleResponse.builder()
                .scheduleType("SEASONAL").principal(P)
                .totalInterest(totalInterest)
                .totalPayable(P.add(totalInterest).setScale(SCALE, ROUND))
                .effectiveRate(req.getNominalRate())
                .numberOfInstallments(tenorMonths).installments(installments).build();
    }

    // ─── 7. GRADUATED — Payments increase 5% per period ─────────────────────

    private ScheduleResponse simulateGraduated(SimulateScheduleRequest req) {
        BigDecimal P = req.getPrincipal();
        int periods = computePeriods(req);
        if (periods == 0) periods = 1;

        BigDecimal annualRate = req.getNominalRate().divide(BigDecimal.valueOf(100), MC);
        BigDecimal periodRate = annualRate.divide(BigDecimal.valueOf(periodsPerYear(req.getRepaymentFrequency())), MC);
        BigDecimal growthRate = new BigDecimal("0.05");

        // Solve for P1: P = sum of P1*(1.05)^(i-1) / (1+r)^i for i=1..n
        // P = P1 * sum((1.05/(1+r))^i / 1.05) ... simplify to geometric series
        // P1 * sum_{i=0}^{n-1} [(1.05)^i / (1+r)^(i+1)]
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal oneOverOnePlusR = BigDecimal.ONE.divide(BigDecimal.ONE.add(periodRate), MC);
        for (int i = 0; i < periods; i++) {
            BigDecimal numerator = BigDecimal.ONE.add(growthRate).pow(i, MC);
            BigDecimal denominator = BigDecimal.ONE.add(periodRate).pow(i + 1, MC);
            sum = sum.add(numerator.divide(denominator, MC));
        }
        BigDecimal p1 = (sum.compareTo(BigDecimal.ZERO) == 0) ? P : P.divide(sum, MC);

        List<InstallmentResponse> installments = new ArrayList<>();
        BigDecimal outstanding = P;
        BigDecimal totalInterest = BigDecimal.ZERO;
        LocalDate dueDate = req.getDisbursementDate();

        for (int i = 1; i <= periods; i++) {
            dueDate = dueDate.plusDays(req.getRepaymentFrequency().getDaysInPeriod() > 0
                    ? req.getRepaymentFrequency().getDaysInPeriod() : req.getTenorDays() / periods);

            BigDecimal payment = p1.multiply(BigDecimal.ONE.add(growthRate).pow(i - 1, MC), MC)
                    .setScale(SCALE, ROUND);
            BigDecimal interest = outstanding.multiply(periodRate, MC).setScale(SCALE, ROUND);
            BigDecimal principal = payment.subtract(interest).setScale(SCALE, ROUND);

            if (i == periods) {
                // Settle remaining
                principal = outstanding;
                payment = principal.add(interest);
            } else if (principal.compareTo(outstanding) > 0) {
                principal = outstanding;
                payment = principal.add(interest);
            }

            outstanding = outstanding.subtract(principal).setScale(SCALE, ROUND);
            totalInterest = totalInterest.add(interest);

            installments.add(InstallmentResponse.builder()
                    .installmentNumber(i).dueDate(dueDate)
                    .principal(principal).interest(interest)
                    .totalPayment(payment).outstandingBalance(outstanding).build());
        }

        return ScheduleResponse.builder()
                .scheduleType("GRADUATED").principal(P)
                .totalInterest(totalInterest.setScale(SCALE, ROUND))
                .totalPayable(P.add(totalInterest).setScale(SCALE, ROUND))
                .effectiveRate(req.getNominalRate())
                .numberOfInstallments(periods).installments(installments).build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int computePeriods(SimulateScheduleRequest req) {
        int daysInPeriod = req.getRepaymentFrequency().getDaysInPeriod();
        if (daysInPeriod == 0) return 1; // BULLET
        return Math.max(1, (int) Math.ceil((double) req.getTenorDays() / daysInPeriod));
    }

    private int periodsPerYear(RepaymentFrequency freq) {
        return switch (freq) {
            case DAILY -> 365;
            case WEEKLY -> 52;
            case BIWEEKLY -> 26;
            case MONTHLY -> 12;
            case QUARTERLY -> 4;
            case BULLET -> 1;
        };
    }
}
