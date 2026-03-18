package com.athena.lms.product.enums;

public enum RepaymentFrequency {
    DAILY(1),
    WEEKLY(7),
    BIWEEKLY(14),
    MONTHLY(30),
    QUARTERLY(91),
    BULLET(0);  // Single payment at end

    private final int daysInPeriod;

    RepaymentFrequency(int daysInPeriod) {
        this.daysInPeriod = daysInPeriod;
    }

    public int getDaysInPeriod() {
        return daysInPeriod;
    }
}
