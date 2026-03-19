-- Journal Entry Maker-Checker Workflow
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS entry_number SERIAL,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS approved_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS reversed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS original_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN IF NOT EXISTS is_system_generated BOOLEAN NOT NULL DEFAULT false;

-- Backfill existing entries as system-generated
UPDATE journal_entries SET is_system_generated = true WHERE source_event IS NOT NULL AND is_system_generated = false;
UPDATE journal_entries SET created_by = posted_by WHERE created_by IS NULL;

CREATE INDEX IF NOT EXISTS idx_je_entry_number ON journal_entries(tenant_id, entry_number);
CREATE INDEX IF NOT EXISTS idx_je_status ON journal_entries(status);
CREATE INDEX IF NOT EXISTS idx_je_original_entry ON journal_entries(original_entry_id);
