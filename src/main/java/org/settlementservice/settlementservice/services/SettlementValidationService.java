package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationSummaryResponse;

public interface SettlementValidationService {

    void validateSettlement(Long settlementReportId);

    ReconciliationSummaryResponse getSummary(Long accountId, String month);
}
