package org.settlementservice.settlementservice.reconciliation.dtos;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ReconciliationSummaryResponse {

    private Long accountId;
    private String month;
    private long totalTransactions;
    private long resolved;
    private long matched;
    private long unresolved;
    private long mismatched;
    private BigDecimal totalVariance;
}
