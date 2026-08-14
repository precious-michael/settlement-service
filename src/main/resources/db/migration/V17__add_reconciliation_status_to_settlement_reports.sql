ALTER TABLE settlement_reports
    ADD COLUMN reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
