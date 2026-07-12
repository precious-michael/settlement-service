ALTER TABLE accounts ADD COLUMN bank_id BIGINT NULL AFTER account_number;

UPDATE accounts a JOIN settlement_banks b ON a.bank_name = b.name SET a.bank_id = b.id;

ALTER TABLE accounts DROP COLUMN bank_name;

ALTER TABLE accounts MODIFY COLUMN bank_id BIGINT NOT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT fk_accounts_settlement_bank FOREIGN KEY (bank_id) REFERENCES settlement_banks (id),
    ADD KEY idx_accounts_bank_id (bank_id);
