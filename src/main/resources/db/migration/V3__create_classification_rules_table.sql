CREATE TABLE classification_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    regex_pattern VARCHAR(500) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    account_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_classification_rules_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    KEY idx_classification_rules_account_id (account_id)
);
