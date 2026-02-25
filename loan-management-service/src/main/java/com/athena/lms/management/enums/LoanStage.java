package com.athena.lms.management.enums;

public enum LoanStage {
    PERFORMING,       // DPD 0
    WATCH,            // DPD 1-30
    SUBSTANDARD,      // DPD 31-90
    DOUBTFUL,         // DPD 91-180
    LOSS              // DPD > 180
}
