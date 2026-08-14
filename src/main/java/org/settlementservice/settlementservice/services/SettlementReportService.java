package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SettlementReportService {

    SettlementReportUploadResponse upload(Long transactionId, MultipartFile file, Long formulaId);

    SettlementReportUploadResponse getStatus(Long id);

    /**
     * Update the reconciliation formula for a settlement report.
     * Recomputes all reconciliation references and resets status to PENDING.
     */
    void updateReconciliationFormula(Long reportId, Long formulaId);

    /**
     * Get settlement report by transaction ID.
     */
    SettlementReportUploadResponse getByTransactionId(Long transactionId);
}
