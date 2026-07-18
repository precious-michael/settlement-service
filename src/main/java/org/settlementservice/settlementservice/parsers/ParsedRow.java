package org.settlementservice.settlementservice.parsers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row from either upload type. Bank statement rows populate every field; settlement report
 * rows leave {@code valueDate} and {@code balance} null since a settlement line has neither.
 */
@Getter
@Setter
@AllArgsConstructor
public class ParsedRow {

    private final int rowNumber;
    private final LocalDate transactionDate;
    private final LocalDate valueDate;
    private final String narration;
    private final String referenceNumber;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private final BigDecimal balance;

}
