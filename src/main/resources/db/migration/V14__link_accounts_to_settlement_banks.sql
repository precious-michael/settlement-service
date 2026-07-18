-- Add the new column after the account number column, not the end
ALTER TABLE accounts ADD COLUMN bank_id BIGINT NULL AFTER account_number;

-- Backfill: For every existing account row, look up its bank_name text against the settlement_banks table
-- by matching names, and fill in the matching bank's id.
UPDATE accounts a JOIN settlement_banks b ON a.bank_name = b.name SET a.bank_id = b.id;

-- Remove the old bank_name column
ALTER TABLE accounts DROP COLUMN bank_name;

-- Make the Column required
ALTER TABLE accounts MODIFY COLUMN bank_id BIGINT NOT NULL;

-- Add the actual Foreign key constraint and index for fast lookups
ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_settlement_bank FOREIGN KEY (bank_id) REFERENCES settlement_banks (id),
    ADD KEY idx_accounts_bank_id (bank_id);
