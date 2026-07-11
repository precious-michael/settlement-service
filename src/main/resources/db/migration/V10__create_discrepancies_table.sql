CREATE TABLE discrepancies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    expected_amount DECIMAL(19,4) NOT NULL,
    reported_amount DECIMAL(19,4) NOT NULL,
    difference DECIMAL(19,4) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_discrepancies_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    UNIQUE KEY uk_discrepancies_transaction_id (transaction_id)
);
