package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class SettlementTransactionResponse {
    private Long id;
    private Long settlementReportId;
    private LocalDate transactionDate;
    private LocalDate settlementDate;
    private String narration;
    private String transactionReference;
    private String rrn;
    private String stan;
    private String terminalId;
    private BigDecimal debit;
    private BigDecimal credit;
    private ReconciliationStatus reconciliationStatus;
    private String reconciliationReference;
    private Instant createdAt;
}
