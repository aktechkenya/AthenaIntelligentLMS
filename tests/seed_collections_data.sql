-- Seed realistic collection cases for testing and demo
-- Run: docker exec -i $(docker ps -q -f name=postgres) psql -U athena athena_collections < tests/seed_collections_data.sql

-- Collection cases (10 cases across different stages/priorities)
INSERT INTO collection_cases (id, tenant_id, loan_id, customer_id, case_number, status, priority, current_dpd, current_stage, outstanding_amount, assigned_to, product_type, opened_at, last_action_at, notes, created_at, updated_at)
VALUES
  ('a1000001-0000-0000-0000-000000000001', 'admin', 'b1000001-0000-0000-0000-000000000001', 'CUST-001', 'COL-DEF-1710001', 'OPEN', 'NORMAL', 5, 'WATCH', 25000.00, 'officer', 'PERSONAL_LOAN', NOW() - INTERVAL '5 days', NULL, 'Early delinquency, first missed payment', NOW() - INTERVAL '5 days', NOW()),
  ('a1000001-0000-0000-0000-000000000002', 'admin', 'b1000001-0000-0000-0000-000000000002', 'CUST-002', 'COL-DEF-1710002', 'IN_PROGRESS', 'HIGH', 45, 'SUBSTANDARD', 150000.00, 'officer', 'SME_LOAN', NOW() - INTERVAL '45 days', NOW() - INTERVAL '3 days', 'Borrower claims business downturn, partial payment received', NOW() - INTERVAL '45 days', NOW()),
  ('a1000001-0000-0000-0000-000000000003', 'admin', 'b1000001-0000-0000-0000-000000000003', 'CUST-003', 'COL-DEF-1710003', 'IN_PROGRESS', 'HIGH', 72, 'DOUBTFUL', 500000.00, 'admin', 'SME_LOAN', NOW() - INTERVAL '72 days', NOW() - INTERVAL '10 days', 'Multiple broken promises, field visit scheduled', NOW() - INTERVAL '72 days', NOW()),
  ('a1000001-0000-0000-0000-000000000004', 'admin', 'b1000001-0000-0000-0000-000000000004', 'CUST-004', 'COL-DEF-1710004', 'PENDING_LEGAL', 'CRITICAL', 120, 'LOSS', 1200000.00, NULL, 'ASSET_FINANCE', NOW() - INTERVAL '120 days', NOW() - INTERVAL '30 days', 'Customer absconded, legal notice served', NOW() - INTERVAL '120 days', NOW()),
  ('a1000001-0000-0000-0000-000000000005', 'admin', 'b1000001-0000-0000-0000-000000000005', 'CUST-005', 'COL-DEF-1710005', 'IN_PROGRESS', 'CRITICAL', 95, 'LOSS', 800000.00, 'officer', 'PERSONAL_LOAN', NOW() - INTERVAL '95 days', NOW() - INTERVAL '5 days', 'Restructuring under discussion', NOW() - INTERVAL '95 days', NOW()),
  ('a1000001-0000-0000-0000-000000000006', 'admin', 'b1000001-0000-0000-0000-000000000006', 'CUST-006', 'COL-DEF-1710006', 'OPEN', 'NORMAL', 8, 'WATCH', 35000.00, NULL, 'NANO_LOAN', NOW() - INTERVAL '8 days', NULL, NULL, NOW() - INTERVAL '8 days', NOW()),
  ('a1000001-0000-0000-0000-000000000007', 'admin', 'b1000001-0000-0000-0000-000000000007', 'CUST-007', 'COL-DEF-1710007', 'IN_PROGRESS', 'NORMAL', 20, 'WATCH', 45000.00, 'officer', 'PERSONAL_LOAN', NOW() - INTERVAL '20 days', NOW() - INTERVAL '2 days', 'Promised payment on 25th', NOW() - INTERVAL '20 days', NOW()),
  ('a1000001-0000-0000-0000-000000000008', 'admin', 'b1000001-0000-0000-0000-000000000008', 'CUST-008', 'COL-DEF-1710008', 'IN_PROGRESS', 'HIGH', 55, 'SUBSTANDARD', 280000.00, 'admin', 'SME_LOAN', NOW() - INTERVAL '55 days', NOW() - INTERVAL '7 days', 'Guarantor contacted', NOW() - INTERVAL '55 days', NOW()),
  ('a1000001-0000-0000-0000-000000000009', 'admin', 'b1000001-0000-0000-0000-000000000009', 'CUST-009', 'COL-DEF-1710009', 'CLOSED', 'NORMAL', 0, 'WATCH', 0.00, 'officer', 'PERSONAL_LOAN', NOW() - INTERVAL '30 days', NOW() - INTERVAL '1 day', 'Fully repaid', NOW() - INTERVAL '30 days', NOW()),
  ('a1000001-0000-0000-0000-000000000010', 'admin', 'b1000001-0000-0000-0000-000000000010', 'CUST-010', 'COL-DEF-1710010', 'OPEN', 'LOW', 3, 'WATCH', 12000.00, NULL, 'NANO_LOAN', NOW() - INTERVAL '3 days', NULL, 'Very early, may self-cure', NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- Collection actions (10 actions across cases)
INSERT INTO collection_actions (id, tenant_id, case_id, action_type, outcome, notes, contact_person, contact_method, performed_by, performed_at, next_action_date, created_at)
VALUES
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000002', 'PHONE_CALL', 'CONTACTED', 'Borrower acknowledged debt, says business is slow', 'John Mwangi', 'PHONE', 'officer', NOW() - INTERVAL '10 days', (NOW() - INTERVAL '3 days')::date, NOW() - INTERVAL '10 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000002', 'PHONE_CALL', 'PROMISE_RECEIVED', 'Promised KES 30,000 by end of week', 'John Mwangi', 'PHONE', 'officer', NOW() - INTERVAL '3 days', (NOW() + INTERVAL '4 days')::date, NOW() - INTERVAL '3 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000003', 'PHONE_CALL', 'NO_ANSWER', 'No answer on primary number', NULL, 'PHONE', 'admin', NOW() - INTERVAL '20 days', (NOW() - INTERVAL '17 days')::date, NOW() - INTERVAL '20 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000003', 'SMS', 'OTHER', 'Sent demand notice via SMS', NULL, 'SMS', 'admin', NOW() - INTERVAL '17 days', (NOW() - INTERVAL '10 days')::date, NOW() - INTERVAL '17 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000003', 'FIELD_VISIT', 'CONTACTED', 'Met borrower at shop, says funds coming from Nairobi', 'Mary Wanjiku', 'IN_PERSON', 'admin', NOW() - INTERVAL '10 days', (NOW() - INTERVAL '3 days')::date, NOW() - INTERVAL '10 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000004', 'LEGAL_NOTICE', 'OTHER', 'Demand letter sent via registered mail', NULL, 'MAIL', 'admin', NOW() - INTERVAL '30 days', NULL, NOW() - INTERVAL '30 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000005', 'PHONE_CALL', 'CONTACTED', 'Discussed restructuring options', 'Peter Ochieng', 'PHONE', 'officer', NOW() - INTERVAL '5 days', (NOW() + INTERVAL '2 days')::date, NOW() - INTERVAL '5 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000007', 'PHONE_CALL', 'PROMISE_RECEIVED', 'Will pay on the 25th', 'Alice Achieng', 'PHONE', 'officer', NOW() - INTERVAL '2 days', (NOW() + INTERVAL '5 days')::date, NOW() - INTERVAL '2 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000008', 'PHONE_CALL', 'NO_ANSWER', 'Borrower not reachable', NULL, 'PHONE', 'admin', NOW() - INTERVAL '14 days', (NOW() - INTERVAL '7 days')::date, NOW() - INTERVAL '14 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000008', 'PHONE_CALL', 'CONTACTED', 'Spoke to guarantor, will follow up with borrower', 'David Kamau', 'PHONE', 'admin', NOW() - INTERVAL '7 days', (NOW() + INTERVAL '1 day')::date, NOW() - INTERVAL '7 days')
ON CONFLICT DO NOTHING;

-- Promises to pay (5 PTPs across cases)
INSERT INTO promises_to_pay (id, tenant_id, case_id, promised_amount, promise_date, status, notes, created_by, fulfilled_at, broken_at, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000002', 30000.00, (NOW() + INTERVAL '4 days')::date, 'PENDING', 'Promised during phone call', 'officer', NULL, NULL, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000003', 100000.00, (NOW() - INTERVAL '5 days')::date, 'BROKEN', 'Promised full amount from Nairobi funds', 'admin', NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '10 days', NOW() - INTERVAL '1 day'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000005', 200000.00, (NOW() + INTERVAL '14 days')::date, 'PENDING', 'Partial payment while restructuring is processed', 'officer', NULL, NULL, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000007', 45000.00, (NOW() + INTERVAL '5 days')::date, 'PENDING', 'Full repayment promised on 25th', 'officer', NULL, NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
  (gen_random_uuid(), 'admin', 'a1000001-0000-0000-0000-000000000009', 50000.00, (NOW() - INTERVAL '5 days')::date, 'FULFILLED', 'Final payment', 'officer', NOW() - INTERVAL '2 days', NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- Collection strategies (5 rules)
INSERT INTO collection_strategies (id, tenant_id, name, product_type, dpd_from, dpd_to, action_type, priority, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'admin', 'Auto SMS - Early Delinquency', NULL, 1, 7, 'SMS', 1, true, NOW(), NOW()),
  (gen_random_uuid(), 'admin', 'Phone Call - Watch', NULL, 8, 30, 'PHONE_CALL', 2, true, NOW(), NOW()),
  (gen_random_uuid(), 'admin', 'Field Visit - Substandard', NULL, 31, 60, 'FIELD_VISIT', 3, true, NOW(), NOW()),
  (gen_random_uuid(), 'admin', 'Legal Notice - Doubtful+', NULL, 61, 999, 'LEGAL_NOTICE', 4, true, NOW(), NOW()),
  (gen_random_uuid(), 'admin', 'Nano SMS Reminder', 'NANO_LOAN', 1, 14, 'SMS', 1, true, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Collection officers (2 officers)
INSERT INTO collection_officers (id, tenant_id, username, max_cases, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'admin', 'officer', 30, true, NOW(), NOW()),
  (gen_random_uuid(), 'admin', 'admin', 50, true, NOW(), NOW())
ON CONFLICT DO NOTHING;
