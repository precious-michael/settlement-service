-- Update reconciliation status column
-- Each settlement transaction line is reconciled individually, not the report as a whole
-- The column already exists from V8, so update it and add index

-- Update existing reconciliation_status values from old UNMATCHED to new PENDING
UPDATE settlement_transactions
SET reconciliation_status = 'PENDING'
WHERE reconciliation_status = 'UNMATCHED';

-- Add index for the reconciliation status column
CREATE INDEX idx_settlement_transactions_recon_status ON settlement_transactions (reconciliation_status);

-- Make settlement_reports.reconciliation_status nullable since it's now derived from transaction statuses
ALTER TABLE settlement_reports
    MODIFY COLUMN reconciliation_status VARCHAR(30) NULL
    COMMENT 'Derived from child settlement_transactions - null until first reconciliation run';
