-- Discrepancies should be per settlement transaction, not per bank statement transaction
-- This allows multiple mini-transactions to have their own discrepancies
-- Database was just recreated, so no data migration needed

ALTER TABLE discrepancies
DROP FOREIGN KEY fk_discrepancies_transaction;

ALTER TABLE discrepancies
DROP INDEX uk_discrepancies_transaction_id;

-- Make transaction_id nullable since discrepancies can exist without a parent transaction
ALTER TABLE discrepancies
MODIFY COLUMN transaction_id BIGINT NULL;

ALTER TABLE discrepancies
ADD COLUMN settlement_transaction_id BIGINT NOT NULL AFTER transaction_id;

-- Add foreign key to settlement_transactions
ALTER TABLE discrepancies
ADD CONSTRAINT fk_discrepancies_settlement_transaction
FOREIGN KEY (settlement_transaction_id) REFERENCES settlement_transactions (id);

-- Add unique constraint on settlement_transaction_id
ALTER TABLE discrepancies
ADD UNIQUE KEY uk_discrepancies_settlement_transaction_id (settlement_transaction_id);
