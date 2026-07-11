CREATE TABLE settlement_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    upload_date DATETIME NOT NULL,
    total_entries INT DEFAULT 0,
    processed_entries INT DEFAULT 0,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_settlement_reports_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_settlement_reports_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    UNIQUE KEY uk_settlement_reports_transaction_id (transaction_id),
    KEY idx_settlement_reports_account_id (account_id)
);
