package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BaseEntity;

/**
 * Defines a reconciliation matching formula for an account.
 * Multiple formulas can exist per account to handle different settlement report formats.
 * Formula uses ${field} placeholders that are replaced with actual values at runtime.
 */
@Getter
@Setter
@Entity
@Table(name = "reconciliation_formulas")
public class ReconciliationFormula extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * User-friendly name for this formula.
     * Examples: "NIP Format", "POS Format", "Terminal-Based Format"
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Formula template using ${field} placeholders.
     * Examples: "${rrn}/${stan}", "${terminalId}|${transactionDate}", "${referenceNumber}"
     */
    @Column(nullable = false, length = 500)
    private String formula;

    /**
     * Optional description explaining when to use this formula.
     */
    @Column(length = 1000)
    private String description;

    /**
     * Whether this is the default formula for the account.
     * Only one formula per account can be default.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    /**
     * Whether this formula is currently active.
     * Inactive formulas are kept for historical reference but not used for new reconciliations.
     */
    @Column(nullable = false)
    private boolean active = true;
}
