package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "discrepancies")
public class Discrepancy extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_transaction_id", nullable = false, unique = true)
    private SettlementTransaction settlementTransaction;

    // Expected (Internal Record) amounts
    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;  // Kept for backward compatibility

    @Column(name = "expected_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedDebit;

    @Column(name = "expected_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedCredit;

    // Reported (Settlement Transaction) amounts
    @Column(name = "reported_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reportedAmount;  // Kept for backward compatibility

    @Column(name = "reported_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal reportedDebit;

    @Column(name = "reported_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal reportedCredit;

    // Absolute difference
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal difference;

    // What fields were used to match these records
    @Column(name = "matched_on", length = 500)
    private String matchedOn;
}
