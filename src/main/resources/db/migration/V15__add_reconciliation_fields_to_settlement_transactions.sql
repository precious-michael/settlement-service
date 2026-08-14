ALTER TABLE settlement_transactions
    ADD COLUMN settlement_date DATE NULL AFTER transaction_date,
    ADD COLUMN rrn VARCHAR(50) NULL AFTER transaction_reference,
    ADD COLUMN stan VARCHAR(50) NULL AFTER rrn,
    ADD COLUMN terminal_id VARCHAR(50) NULL AFTER stan;
