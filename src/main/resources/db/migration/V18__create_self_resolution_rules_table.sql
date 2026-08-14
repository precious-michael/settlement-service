CREATE TABLE self_resolution_rules (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    pattern TEXT         NOT NULL,
    active  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_self_resolution_rules_active (active)
);

INSERT INTO self_resolution_rules (name, pattern) VALUES
    ('NIP Transfer',       'NIP/(?<rrn>[A-Z0-9]+)/(?<ref>[^/]+)/(?<stan>[0-9]+)'),
    ('Card POS Settlement','(?i)POS.+TERM:(?<terminalId>[A-Z0-9]{8,}).+RRN:(?<rrn>[0-9]+)'),
    ('USSD Transfer',      '(?i)USSD.+REF:(?<ref>[A-Z0-9]+)');
