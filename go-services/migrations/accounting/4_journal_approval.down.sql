ALTER TABLE journal_entries
    DROP COLUMN IF EXISTS entry_number,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS approved_by,
    DROP COLUMN IF EXISTS approved_at,
    DROP COLUMN IF EXISTS rejection_reason,
    DROP COLUMN IF EXISTS reversed_by,
    DROP COLUMN IF EXISTS reversed_at,
    DROP COLUMN IF EXISTS reversal_reason,
    DROP COLUMN IF EXISTS original_entry_id,
    DROP COLUMN IF EXISTS is_system_generated;
