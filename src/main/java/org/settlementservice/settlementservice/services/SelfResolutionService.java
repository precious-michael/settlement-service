package org.settlementservice.settlementservice.services;

public interface SelfResolutionService {

    /**
     * Attempts to self-resolve UNRESOLVED transactions. Scope is controlled by the optional
     * filters: if {@code accountId} is provided, only that account's transactions are processed;
     * if {@code statementId} is provided, only transactions from that bank statement are processed;
     * if both are null, all UNRESOLVED transactions are processed. Returns the count resolved.
     */
    int resolve(Long accountId, Long statementId);

    /**
     * Attempts to self-resolve a single UNRESOLVED transaction. Returns true if resolved,
     * false if skipped (already resolved, null narration, or no rule matched).
     */
    boolean resolveOne(Long transactionId);
}
