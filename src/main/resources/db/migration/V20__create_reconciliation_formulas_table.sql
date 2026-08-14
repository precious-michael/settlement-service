-- Create reconciliation_formulas table
-- Defines matching formulas for reconciling settlement transactions to internal records
-- Multiple formulas per account support different settlement report formats

CREATE TABLE reconciliation_formulas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL COMMENT 'User-friendly name for this formula',
    formula VARCHAR(500) NOT NULL COMMENT 'Template with field placeholders',
    description VARCHAR(1000) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Default formula for this account',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reconciliation_formulas_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    KEY idx_reconciliation_formulas_account (account_id),
    KEY idx_reconciliation_formulas_account_default (account_id, is_default)
);
