package com.athena.lms.product.enums;

public enum ScheduleType {
    EMI,          // Reducing balance equal monthly installments
    FLAT,         // Flat interest on original principal
    FLAT_RATE,    // Alias for FLAT — flat interest on original principal
    ACTUARIAL,    // Actuarial / internal rate of return method
    DAILY_SIMPLE, // Simple daily interest
    BALLOON,      // Interest-only payments + bullet principal at end
    SEASONAL,     // Irregular payments aligned to harvest/income cycles
    GRADUATED     // Increasing payments (5% growth per period)
}
