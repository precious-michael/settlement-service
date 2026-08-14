package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class TransactionResponse {

    private Long id;
    private Long bankStatementId;
    private Long accountId;
    private LocalDate transactionDate;
    private LocalDate valueDate;
    private String narration;
    private String referenceNumber;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal balance;
    private ProductType productType;
    private TransactionStatus status;
    private Instant createdAt;
}
