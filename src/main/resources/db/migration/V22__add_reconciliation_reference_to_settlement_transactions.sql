-- Add reconciliation_reference column to settlement_transactions
-- Stores the computed reference formula result for matching to internal records

ALTER TABLE settlement_transactions
    ADD COLUMN reconciliation_reference VARCHAR(500) NULL
    COMMENT 'Computed from formula for matching to internal records';
