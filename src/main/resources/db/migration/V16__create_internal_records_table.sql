-- Create internal_records table - global transaction records from core banking
-- Independent of settlement accounts - represents complete transactions with source and destination

CREATE TABLE internal_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Transaction identifiers
    reference_number VARCHAR(255) NOT NULL,
    rrn VARCHAR(50) NULL COMMENT 'Retrieval Reference Number',
    stan VARCHAR(50) NULL COMMENT 'System Trace Audit Number',
    terminal_id VARCHAR(50) NULL COMMENT 'Terminal ID (TID)',
    pan VARCHAR(50) NULL COMMENT 'Masked Primary Account Number',
    processor_reference VARCHAR(255) NULL,
    session_id VARCHAR(100) NULL,

    -- Source (from) details
    source_account_number VARCHAR(50) NULL,
    source_account_name VARCHAR(255) NULL,
    source_bank_code VARCHAR(20) NULL,
    source_bank_name VARCHAR(255) NULL,

    -- Destination (to) details
    destination_account_number VARCHAR(50) NULL,
    destination_account_name VARCHAR(255) NULL,
    destination_bank_code VARCHAR(20) NULL,
    destination_bank_name VARCHAR(255) NULL,

    -- Transaction details
    transaction_date DATE NOT NULL,
    transaction_time TIME NULL,
    transaction_type VARCHAR(50) NULL COMMENT 'NIP, POS, USSD, etc.',
    product_type VARCHAR(50) NULL,
    narration TEXT NULL,

    -- Amounts
    debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    amount DECIMAL(19,4) NULL,
    currency VARCHAR(10) NULL,

    -- Additional fields
    card_acceptor_id VARCHAR(100) NULL,
    status VARCHAR(50) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Indexes for efficient reconciliation lookups
    KEY idx_internal_records_reference (reference_number),
    KEY idx_internal_records_rrn (rrn),
    KEY idx_internal_records_rrn_stan (rrn, stan),
    KEY idx_internal_records_terminal_date (terminal_id, transaction_date),
    KEY idx_internal_records_transaction_date (transaction_date)
);
