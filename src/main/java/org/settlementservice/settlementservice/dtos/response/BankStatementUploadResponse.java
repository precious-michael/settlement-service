package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.BatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class BankStatementUploadResponse {
    private Long id;
    private String fileName;
    private BatchStatus status;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private LocalDate closingDate;
    private Integer totalEntries;
    private Instant uploadDate;
    private String errorMessage;
}
