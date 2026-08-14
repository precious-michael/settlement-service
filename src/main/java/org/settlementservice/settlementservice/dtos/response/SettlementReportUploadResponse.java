package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.ReportReconciliationStatus;

import java.time.Instant;

@Getter
@Setter
public class SettlementReportUploadResponse {
    private Long id;
    private String fileName;
    private BatchStatus status;
    private Instant uploadDate;
    private Integer totalEntries;
    private String errorMessage;
    private ReportReconciliationStatus reconciliationStatus;
    private Long transactionId;
}