package org.settlementservice.settlementservice.reconciliation.services;

import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationRunResponse;

public interface ReconciliationEngine {

    /**
     * Processes all RESOLVED transactions: matches each against its InternalRecord counterpart,
     * marks MATCHED on agreement or MISMATCHED (with a Discrepancy) on divergence.
     * Already-processed transactions (MATCHED/MISMATCHED) are skipped automatically since
     * they are no longer in RESOLVED status.
     */
    ReconciliationRunResponse run();
}
