package org.settlementservice.settlementservice.demo.dtos;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class DemoFileGenerateResponse {
    private Long accountId;
    private int generated;
    private int matchedPairs;
    private int mismatchedPairs;

    // File information - using field names frontend expects
    private String bankStatementFile;
    private String settlementReportFile;  // First settlement report (for backward compatibility)
    private List<String> settlementReportFiles;  // All settlement report files
    private String bankStatementDownloadUrl;
    private String settlementReportDownloadUrl;

    // Transaction summary - using field names frontend expects
    private int transactionCount;
    private int internalRecordsCreated;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private String dateFrom;
    private String dateTo;

    // Instructions for user
    private String instructions;
}
