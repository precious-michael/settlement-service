package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.models.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Global transaction record from internal core banking system.
 * Represents a complete transaction with both source and destination details.
 * Independent of any settlement account - used for reconciliation matching.
 */
@Getter
@Setter
@Entity
@Table(name = "internal_records")
public class InternalRecord extends BaseEntity {

    // ========== Transaction Identifiers ==========

    /**
     * Internal transaction reference number.
     */
    @Column(name = "reference_number", nullable = false, length = 255)
    private String referenceNumber;

    /**
     * Retrieval Reference Number - card scheme correlation key.
     */
    @Column(name = "rrn", length = 50)
    private String rrn;

    /**
     * System Trace Audit Number.
     */
    @Column(name = "stan", length = 50)
    private String stan;

    /**
     * Terminal ID (TID) - for POS/ATM transactions.
     */
    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    /**
     * Masked Primary Account Number.
     */
    @Column(name = "pan", length = 50)
    private String pan;

    /**
     * External processor reference (e.g., NIBSS reference).
     */
    @Column(name = "processor_reference", length = 255)
    private String processorReference;

    /**
     * Session ID for tracking.
     */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    // ========== Source (From) Details ==========

    @Column(name = "source_account_number", length = 50)
    private String sourceAccountNumber;

    @Column(name = "source_account_name", length = 255)
    private String sourceAccountName;

    @Column(name = "source_bank_code", length = 20)
    private String sourceBankCode;

    @Column(name = "source_bank_name", length = 255)
    private String sourceBankName;

    // ========== Destination (To) Details ==========

    @Column(name = "destination_account_number", length = 50)
    private String destinationAccountNumber;

    @Column(name = "destination_account_name", length = 255)
    private String destinationAccountName;

    @Column(name = "destination_bank_code", length = 20)
    private String destinationBankCode;

    @Column(name = "destination_bank_name", length = 255)
    private String destinationBankName;

    // ========== Transaction Details ==========

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "transaction_time")
    private LocalTime transactionTime;

    /**
     * Transaction type: NIP, POS, USSD, WEB, etc.
     */
    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    /**
     * Product type classification.
     */
    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(columnDefinition = "TEXT")
    private String narration;

    // ========== Amounts ==========

    /**
     * Debit amount (for ledger entry format).
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit;

    /**
     * Credit amount (for ledger entry format).
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit;

    /**
     * Transaction amount (single value, can be used instead of debit/credit).
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency;

    // ========== Additional Fields ==========

    /**
     * Card acceptor ID (for POS transactions).
     */
    @Column(name = "card_acceptor_id", length = 100)
    private String cardAcceptorId;

    /**
     * Transaction status.
     */
    @Column(name = "status", length = 50)
    private String status;
}
