CREATE TABLE settlement_report_row_errors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_report_id BIGINT NOT NULL,
    row_num INT NOT NULL,
    raw_row TEXT NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_settlement_report_row_errors_settlement_report FOREIGN KEY (settlement_report_id) REFERENCES settlement_reports (id),
    KEY idx_settlement_report_row_errors_settlement_report_id (settlement_report_id)
);
