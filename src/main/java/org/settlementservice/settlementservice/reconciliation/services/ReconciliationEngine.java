package org.settlementservice.settlementservice.reconciliation.services;

import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationRunResponse;

import java.util.List;
import java.util.Map;

public interface ReconciliationEngine {

    /**
     * Processes all RESOLVED transactions: matches each against its InternalRecord counterpart,
     * marks MATCHED on agreement or MISMATCHED (with a Discrepancy) on divergence.
     * Already-processed transactions (MATCHED/MISMATCHED) are skipped automatically since
     * they are no longer in RESOLVED status.
     */
    ReconciliationRunResponse run();

    /**
     * Asynchronously reconciles a batch of pending/missing settlement transactions.
     * This method is called through the Spring proxy to ensure @Async behavior.
     * Accepts transaction IDs and pre-computed account mapping to avoid Hibernate lazy-loading issues.
     */
    void reconcileAsync(Long taskId, List<Long> settlementTransactionIds, Map<Long, Long> transactionToAccountMap);
}
