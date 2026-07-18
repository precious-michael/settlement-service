package org.settlementservice.settlementservice.parsers;

import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.exceptions.FileParseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStatementFileParserTest {

    private final CsvStatementFileParser parser = new CsvStatementFileParser();

    @Test
    void parseBankStatement_validFile_skipsHeaderBlockAndReturnsRows() {
        String csv = """
                Statement Period Start,2026-06-01
                Statement Period End,2026-06-30
                Currency,NGN
                Opening Balance,150000.00
                Closing Balance,210000.00
                Transaction Date,Value Date,Narration,Reference Number,Debit,Credit,Balance
                2026-06-02,2026-06-02,CARD SETTLEMENT,REF-001,0,5000,155000
                2026-06-03,2026-06-03,PAYROLL,REF-002,0,20000,175000
                """;

        ParsedFile parsed = parser.parseBankStatement(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRowErrors()).isEmpty();
        assertThat(parsed.getRows()).hasSize(2);

        ParsedRow first = parsed.getRows().get(0);
        assertThat(first.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(first.getNarration()).isEqualTo("CARD SETTLEMENT");
        assertThat(first.getReferenceNumber()).isEqualTo("REF-001");
        assertThat(first.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.getCredit()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void parseBankStatement_rowWithUnparseableDebit_capturesRowErrorWithoutFailingOtherRows() {
        String csv = """
                Transaction Date,Value Date,Narration,Reference Number,Debit,Credit,Balance
                2026-06-02,2026-06-02,CARD SETTLEMENT,REF-001,not-a-number,5000,155000
                2026-06-03,2026-06-03,PAYROLL,REF-002,0,20000,175000
                """;

        ParsedFile parsed = parser.parseBankStatement(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRowErrors()).hasSize(1);
        assertThat(parsed.getRowErrors().get(0).getMessage()).contains("Debit");
    }

    @Test
    void parseBankStatement_rowMissingTransactionDate_capturesRowError() {
        String csv = """
                Transaction Date,Value Date,Narration,Reference Number,Debit,Credit,Balance
                ,2026-06-02,CARD SETTLEMENT,REF-001,0,5000,155000
                """;

        ParsedFile parsed = parser.parseBankStatement(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRows()).isEmpty();
        assertThat(parsed.getRowErrors()).hasSize(1);
        assertThat(parsed.getRowErrors().get(0).getMessage()).contains("Transaction Date");
    }

    @Test
    void parseBankStatement_blankRowStopsReadingFurtherRows() {
        String csv = """
                Transaction Date,Value Date,Narration,Reference Number,Debit,Credit,Balance
                2026-06-02,2026-06-02,CARD SETTLEMENT,REF-001,0,5000,155000

                2026-06-04,2026-06-04,PAYROLL,REF-003,0,1000,156000
                """;

        ParsedFile parsed = parser.parseBankStatement(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRows().get(0).getReferenceNumber()).isEqualTo("REF-001");
    }

    @Test
    void parseBankStatement_missingTransactionDateTableHeader_throwsFileParseException() {
        String csv = "Currency,NGN\n";

        assertThatThrownBy(() -> parser.parseBankStatement(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("Transaction Date");
    }

    @Test
    void parseBankStatement_unreadableCsv_throwsFileParseException() {
        String malformed = "\"unterminated quote";

        assertThatThrownBy(() -> parser.parseBankStatement(malformed.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FileParseException.class);
    }

    @Test
    void parseSettlementReport_validFile_returnsAllRows() {
        String csv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,CARD SETTLEMENT,REF-001,0,5000
                2026-06-03,PAYROLL,REF-002,0,20000
                """;

        ParsedFile parsed = parser.parseSettlementReport(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRowErrors()).isEmpty();
        assertThat(parsed.getRows()).hasSize(2);

        ParsedRow first = parsed.getRows().get(0);
        assertThat(first.getRowNumber()).isEqualTo(2);
        assertThat(first.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(first.getNarration()).isEqualTo("CARD SETTLEMENT");
        assertThat(first.getReferenceNumber()).isEqualTo("REF-001");
        assertThat(first.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.getCredit()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void parseSettlementReport_columnOrderDoesNotMatter() {
        String csv = """
                credit,debit,transaction_reference,narration,transaction_date
                5000,0,REF-001,CARD SETTLEMENT,2026-06-02
                """;

        ParsedFile parsed = parser.parseSettlementReport(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRows().get(0).getReferenceNumber()).isEqualTo("REF-001");
    }

    @Test
    void parseSettlementReport_rowWithUnparseableCredit_capturesRowErrorWithoutFailingOtherRows() {
        String csv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,CARD SETTLEMENT,REF-001,0,not-a-number
                2026-06-03,PAYROLL,REF-002,0,20000
                """;

        ParsedFile parsed = parser.parseSettlementReport(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRowErrors()).hasSize(1);
        RowParseError error = parsed.getRowErrors().get(0);
        assertThat(error.getRowNumber()).isEqualTo(2);
        assertThat(error.getMessage()).contains("credit");
    }

    @Test
    void parseSettlementReport_rowMissingNarration_capturesRowError() {
        String csv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,,REF-001,0,5000
                """;

        ParsedFile parsed = parser.parseSettlementReport(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.getRows()).isEmpty();
        assertThat(parsed.getRowErrors()).hasSize(1);
        assertThat(parsed.getRowErrors().get(0).getMessage()).contains("narration");
    }

    @Test
    void parseSettlementReport_missingRequiredColumn_throwsFileParseException() {
        String csv = """
                transaction_date,narration,debit,credit
                2026-06-02,CARD SETTLEMENT,0,5000
                """;

        assertThatThrownBy(() -> parser.parseSettlementReport(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FileParseException.class);
    }
}
