-- Seed default fraud detection rules for all tenants (tenant_id='*' = global)

INSERT INTO fraud_rules (tenant_id, rule_code, rule_name, description, category, severity, event_types, parameters) VALUES
-- ─── Transaction Amount Rules ──────────────────────────────────────────────
('*', 'LARGE_SINGLE_TXN', 'Large Single Transaction',
 'Flags transactions exceeding the CTR reporting threshold (KES 1M)',
 'TRANSACTION', 'HIGH',
 'payment.completed,account.credit.received,transfer.completed',
 '{"threshold": 1000000}'),

('*', 'STRUCTURING', 'Transaction Structuring Detection',
 'Detects multiple transactions just below threshold within 24h window that aggregate above threshold',
 'AML', 'HIGH',
 'payment.completed,account.credit.received,transfer.completed',
 '{"threshold": 1000000, "windowHours": 24, "minTransactions": 3, "perTxnCeiling": 999999}'),

('*', 'ROUND_AMOUNT_PATTERN', 'Round Amount Pattern',
 'Detects unusual frequency of round-number transactions',
 'TRANSACTION', 'LOW',
 'payment.completed,transfer.completed',
 '{"minRoundTxns": 5, "windowHours": 24, "roundThreshold": 10000}'),

-- ─── Velocity Rules ────────────────────────────────────────────────────────
('*', 'HIGH_VELOCITY_1H', 'High Transaction Velocity (1 Hour)',
 'Flags customers with excessive transactions in a 1-hour window',
 'VELOCITY', 'MEDIUM',
 'payment.completed,transfer.completed,account.credit.received,account.debit.processed',
 '{"maxTransactions": 10, "windowMinutes": 60}'),

('*', 'HIGH_VELOCITY_24H', 'High Transaction Velocity (24 Hours)',
 'Flags customers with excessive transactions in a 24-hour window',
 'VELOCITY', 'MEDIUM',
 'payment.completed,transfer.completed,account.credit.received,account.debit.processed',
 '{"maxTransactions": 50, "windowMinutes": 1440}'),

('*', 'RAPID_FUND_MOVEMENT', 'Rapid Fund Movement',
 'Flags funds received and transferred out within minutes (pass-through pattern)',
 'AML', 'HIGH',
 'transfer.completed',
 '{"windowMinutes": 15}'),

-- ─── Loan Application Fraud Rules ──────────────────────────────────────────
('*', 'APPLICATION_STACKING', 'Application Stacking',
 'Detects multiple loan applications submitted within a short window',
 'APPLICATION', 'HIGH',
 'loan.application.submitted',
 '{"maxApplications": 5, "windowDays": 30}'),

('*', 'EARLY_PAYOFF_SUSPICIOUS', 'Suspicious Early Payoff',
 'Flags loans paid off within unusually short period after disbursement',
 'AML', 'MEDIUM',
 'loan.closed',
 '{"minDaysForAlert": 30}'),

('*', 'LOAN_CYCLING', 'Loan Cycling Detection',
 'Detects rapid loan close → new application pattern (potential layering)',
 'AML', 'HIGH',
 'loan.application.submitted',
 '{"windowDays": 7}'),

-- ─── Account Rules ─────────────────────────────────────────────────────────
('*', 'DORMANT_REACTIVATION', 'Dormant Account Reactivation',
 'Flags activity on accounts dormant for extended period',
 'ACCOUNT', 'MEDIUM',
 'account.unfrozen,account.credit.received',
 '{"dormantDays": 180}'),

('*', 'KYC_BYPASS_ATTEMPT', 'KYC Bypass Attempt',
 'Flags transactions on accounts with pending/failed KYC',
 'COMPLIANCE', 'HIGH',
 'payment.completed,transfer.completed,loan.application.submitted',
 '{}'),

-- ─── Overdraft / BNPL Rules ────────────────────────────────────────────────
('*', 'OVERDRAFT_RAPID_DRAW', 'Rapid Overdraft Drawdown',
 'Flags immediate full drawdown of newly approved overdraft facility',
 'OVERDRAFT', 'MEDIUM',
 'overdraft.drawn',
 '{"drawdownThresholdPercent": 90, "windowMinutes": 60}'),

('*', 'BNPL_ABUSE', 'BNPL Abuse Pattern',
 'Detects rapid sequential BNPL approvals with minimal deposits',
 'APPLICATION', 'HIGH',
 'shop.bnpl.approved',
 '{"maxApprovals": 3, "windowDays": 7, "minDepositPercent": 5}'),

-- ─── Payment Anomaly Rules ─────────────────────────────────────────────────
('*', 'PAYMENT_REVERSAL_ABUSE', 'Payment Reversal Abuse',
 'Flags customers with high ratio of reversed to completed payments',
 'TRANSACTION', 'HIGH',
 'payment.reversed',
 '{"maxReversalPercent": 30, "minPayments": 5, "windowDays": 30}'),

('*', 'OVERPAYMENT', 'Loan Overpayment',
 'Flags payments exceeding the total outstanding loan balance',
 'AML', 'HIGH',
 'payment.completed',
 '{"overpaymentThresholdPercent": 110}'),

-- ─── Write-off / Collections Fraud ──────────────────────────────────────────
('*', 'SUSPICIOUS_WRITEOFF', 'Suspicious Write-off',
 'Flags write-offs on loans with recent payment activity',
 'INTERNAL', 'HIGH',
 'loan.written.off',
 '{"recentPaymentDays": 30}'),

('*', 'PROMISE_TO_PAY_GAMING', 'Promise-to-Pay Gaming',
 'Detects customers repeatedly making unfulfilled payment promises',
 'COLLECTIONS', 'MEDIUM',
 'loan.dpd.updated',
 '{"maxUnfulfilledPromises": 3, "windowDays": 90}'),

-- ─── Watchlist Rules ────────────────────────────────────────────────────────
('*', 'WATCHLIST_MATCH', 'Watchlist Match',
 'Flags customers matching PEP, sanctions, or internal blacklist entries',
 'COMPLIANCE', 'CRITICAL',
 'customer.created,customer.updated,loan.application.submitted',
 '{}');
