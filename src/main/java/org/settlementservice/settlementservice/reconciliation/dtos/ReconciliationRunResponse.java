package org.settlementservice.settlementservice.reconciliation.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReconciliationRunResponse {
    private int totalProcessed;
    private int matched;
    private int mismatched;
    private int noMatchFound;
}
