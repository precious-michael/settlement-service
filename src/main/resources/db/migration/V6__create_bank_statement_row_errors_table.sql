CREATE TABLE bank_statement_row_errors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_statement_id BIGINT NOT NULL,
    row_num INT NOT NULL,
    raw_row TEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_bank_statement_row_errors_bank_statement FOREIGN KEY (bank_statement_id) REFERENCES bank_statements (id),
    KEY idx_bank_statement_row_errors_bank_statement_id (bank_statement_id)
);
