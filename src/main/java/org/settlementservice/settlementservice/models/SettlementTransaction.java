package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "settlement_transactions")
public class SettlementTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_report_id", nullable = false)
    private SettlementReport settlementReport;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /**
     * When the bank/PSP actually settled this line — distinct from {@code transactionDate},
     * since settlement typically lags the original transaction. Not present in every settlement
     * report format, so left null when the source file doesn't carry it.
     */
    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String narration;

    @Column(name = "transaction_reference", nullable = false, length = 255)
    private String transactionReference;

    /**
     * Retrieval Reference Number — the card-scheme correlation key. Optional: not every
     * settlement report format includes it.
     */
    @Column(name = "rrn", length = 50)
    private String rrn;

    /**
     * System Trace Audit Number. Optional, same reasoning as {@link #rrn}.
     */
    @Column(name = "stan", length = 50)
    private String stan;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit;
}
