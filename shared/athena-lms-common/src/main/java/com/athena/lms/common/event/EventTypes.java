package com.athena.lms.common.event;

/**
 * Canonical event type constants for all LMS domain events.
 * These strings are used as RabbitMQ routing keys.
 */
public final class EventTypes {

    private EventTypes() {}

    // ─── Account events ────────────────────────────────────────────────────────
    public static final String ACCOUNT_CREATED          = "account.created";
    public static final String ACCOUNT_CREDIT_RECEIVED  = "account.credit.received";
    public static final String ACCOUNT_DEBIT_PROCESSED  = "account.debit.processed";
    public static final String ACCOUNT_FROZEN           = "account.frozen";
    public static final String ACCOUNT_UNFROZEN         = "account.unfrozen";
    public static final String ACCOUNT_CLOSED           = "account.closed";

    // ─── Loan origination events ───────────────────────────────────────────────
    public static final String LOAN_APPLICATION_SUBMITTED  = "loan.application.submitted";
    public static final String LOAN_APPLICATION_APPROVED   = "loan.application.approved";
    public static final String LOAN_APPLICATION_REJECTED   = "loan.application.rejected";
    public static final String LOAN_DOCUMENTS_VERIFIED     = "loan.documents.verified";
    public static final String LOAN_CREDIT_ASSESSED        = "loan.credit.assessed";

    // ─── Loan management events ────────────────────────────────────────────────
    public static final String LOAN_DISBURSED          = "loan.disbursed";
    public static final String LOAN_REPAYMENT_RECEIVED = "loan.repayment.received";
    public static final String LOAN_DPD_UPDATED        = "loan.dpd.updated";
    public static final String LOAN_STAGE_CHANGED      = "loan.stage.changed";
    public static final String LOAN_CLOSED             = "loan.closed";
    public static final String LOAN_WRITTEN_OFF        = "loan.written.off";
    public static final String LOAN_MODIFIED           = "loan.modified";

    // ─── Payment events ────────────────────────────────────────────────────────
    public static final String PAYMENT_INITIATED   = "payment.initiated";
    public static final String PAYMENT_COMPLETED   = "payment.completed";
    public static final String PAYMENT_FAILED      = "payment.failed";
    public static final String PAYMENT_REVERSED    = "payment.reversed";

    // ─── Float events ──────────────────────────────────────────────────────────
    public static final String FLOAT_DRAWN              = "float.drawn";
    public static final String FLOAT_REPAID             = "float.repaid";
    public static final String FLOAT_FEE_CHARGED        = "float.fee.charged";
    public static final String FLOAT_RESTRICTION_APPLIED = "float.restriction.applied";
    public static final String FLOAT_LIMIT_CHANGED      = "float.limit.changed";

    // ─── AML / compliance events ───────────────────────────────────────────────
    public static final String AML_ALERT_RAISED    = "aml.alert.raised";
    public static final String AML_SAR_FILED       = "aml.sar.filed";
    public static final String CUSTOMER_KYC_PASSED = "customer.kyc.passed";
    public static final String CUSTOMER_KYC_FAILED = "customer.kyc.failed";
}
