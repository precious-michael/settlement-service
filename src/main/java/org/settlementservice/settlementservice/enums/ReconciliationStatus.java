package org.settlementservice.settlementservice.enums;

/**
 * Reconciliation status for individual SettlementTransaction entries.
 * Each settlement transaction line item is matched against an InternalRecord
 * and assigned one of these statuses.
 */
public enum ReconciliationStatus {
    /**
     * Initial state — reconciliation has not yet been attempted for this transaction.
     */
    PENDING,

    /**
     * Matching InternalRecord found and amounts match.
     */
    RECONCILED,

    /**
     * Matching InternalRecord found but amounts differ — creates a Discrepancy.
     */
    UNRECONCILED,

    /**
     * No matching InternalRecord found using the account's reconciliation strategy.
     */
    MISSING
}
