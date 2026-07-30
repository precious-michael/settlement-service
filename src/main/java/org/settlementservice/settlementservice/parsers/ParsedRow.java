package org.settlementservice.settlementservice.parsers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row from either upload type. Bank statement rows populate every field through
 * {@code balance} and leave {@code settlementDate}/{@code rrn}/{@code stan}/{@code terminalId}
 * null (those are settlement-report-only concepts). Settlement report rows leave
 * {@code valueDate} and {@code balance} null since a settlement line has neither, and populate
 * {@code settlementDate}/{@code rrn}/{@code stan}/{@code terminalId} only when the source file
 * carries them — all four are optional even for settlement reports.
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
    private final LocalDate settlementDate;
    private final String rrn;
    private final String stan;
    private final String terminalId;

}
