CREATE TABLE settlement_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_report_id BIGINT NOT NULL,
    transaction_date DATE NOT NULL,
    narration TEXT NOT NULL,
    transaction_reference VARCHAR(255) NOT NULL,
    debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    reconciliation_status VARCHAR(50) NOT NULL DEFAULT 'UNMATCHED',
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_settlement_transactions_settlement_report FOREIGN KEY (settlement_report_id) REFERENCES settlement_reports (id),
    KEY idx_settlement_transactions_settlement_report_id (settlement_report_id)
);

