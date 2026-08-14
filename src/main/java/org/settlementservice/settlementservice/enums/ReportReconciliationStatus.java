package org.settlementservice.settlementservice.enums;

public enum ReportReconciliationStatus {
    /**
     * Reconciliation not yet attempted for this settlement report's transactions.
     */
    PENDING,

    /**
     * All settlement transactions are reconciled (amounts match internal records).
     */
    RECONCILED,

    /**
     * Some transactions reconciled, some not (mix of RECONCILED, MISSING, UNRECONCILED).
     */
    PARTIAL_RECONCILIATION,

    /**
     * All transactions have issues (UNRECONCILED or MISSING, none RECONCILED).
     */
    UNRECONCILED
}
