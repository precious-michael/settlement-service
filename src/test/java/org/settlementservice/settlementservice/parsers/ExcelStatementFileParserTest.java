package org.settlementservice.settlementservice.parsers;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.exceptions.FileParseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelStatementFileParserTest {

    private final ExcelStatementFileParser parser = new ExcelStatementFileParser();

    @Test
    void parseBankStatement_validFile_skipsHeaderBlockAndReturnsRows() {
        byte[] file = workbook(sheet -> {
            writeHeaderRow(sheet, 0, "Statement Period Start", "2026-06-01");
            writeHeaderRow(sheet, 1, "Statement Period End", "2026-06-30");
            writeHeaderRow(sheet, 2, "Currency", "NGN");
            writeHeaderRow(sheet, 3, "Opening Balance", "150000.00");
            writeHeaderRow(sheet, 4, "Closing Balance", "210000.00");
            writeTableHeader(sheet, 6);
            writeDataRow(sheet, 7, "2026-06-02", "2026-06-02", "CARD SETTLEMENT", "REF-001", "0", "5000", "155000");
            writeDataRow(sheet, 8, "2026-06-03", "2026-06-03", "PAYROLL", "REF-002", "0", "20000", "175000");
        });

        ParsedFile parsed = parser.parseBankStatement(file);

        assertThat(parsed.getRowErrors()).isEmpty();
        assertThat(parsed.getRows()).hasSize(2);

        ParsedRow first = parsed.getRows().get(0);
        assertThat(first.getRowNumber()).isEqualTo(8);
        assertThat(first.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(first.getNarration()).isEqualTo("CARD SETTLEMENT");
        assertThat(first.getReferenceNumber()).isEqualTo("REF-001");
        assertThat(first.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(first.getCredit()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void parseBankStatement_rowWithUnparseableDebit_capturesRowErrorWithoutFailingOtherRows() {
        byte[] file = workbook(sheet -> {
            writeTableHeader(sheet, 0);
            writeDataRow(sheet, 1, "2026-06-02", "2026-06-02", "CARD SETTLEMENT", "REF-001", "not-a-number", "5000", "155000");
            writeDataRow(sheet, 2, "2026-06-03", "2026-06-03", "PAYROLL", "REF-002", "0", "20000", "175000");
        });

        ParsedFile parsed = parser.parseBankStatement(file);

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRowErrors()).hasSize(1);
        RowParseError error = parsed.getRowErrors().get(0);
        assertThat(error.getRowNumber()).isEqualTo(2);
        assertThat(error.getMessage()).contains("Debit");
    }

    @Test
    void parseBankStatement_rowMissingTransactionDate_capturesRowError() {
        byte[] file = workbook(sheet -> {
            writeTableHeader(sheet, 0);
            writeDataRow(sheet, 1, "", "2026-06-02", "CARD SETTLEMENT", "REF-001", "0", "5000", "155000");
        });

        ParsedFile parsed = parser.parseBankStatement(file);

        assertThat(parsed.getRows()).isEmpty();
        assertThat(parsed.getRowErrors()).hasSize(1);
        assertThat(parsed.getRowErrors().get(0).getMessage()).contains("Transaction Date");
    }

    @Test
    void parseBankStatement_blankRowStopsReadingFurtherRows() {
        byte[] file = workbook(sheet -> {
            writeTableHeader(sheet, 0);
            writeDataRow(sheet, 1, "2026-06-02", "2026-06-02", "CARD SETTLEMENT", "REF-001", "0", "5000", "155000");
            // row 2 left entirely blank
            writeDataRow(sheet, 3, "2026-06-04", "2026-06-04", "PAYROLL", "REF-003", "0", "1000", "156000");
        });

        ParsedFile parsed = parser.parseBankStatement(file);

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRows().get(0).getReferenceNumber()).isEqualTo("REF-001");
    }

    @Test
    void parseBankStatement_missingTransactionDateTableHeader_throwsFileParseException() {
        byte[] file = workbook(sheet -> writeHeaderRow(sheet, 0, "Currency", "NGN"));

        assertThatThrownBy(() -> parser.parseBankStatement(file))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("Transaction Date");
    }

    @Test
    void parseBankStatement_corruptBytes_throwsFileParseException() {
        assertThatThrownBy(() -> parser.parseBankStatement("not a real workbook".getBytes()))
                .isInstanceOf(FileParseException.class);
    }

    @Test
    void parseSettlementReport_validFile_returnsAllRows() {
        byte[] file = workbook(sheet -> {
            writeSettlementHeaderRow(sheet, 0);
            writeSettlementDataRow(sheet, 1, "2026-06-02", "CARD SETTLEMENT", "REF-001", "0", "5000");
            writeSettlementDataRow(sheet, 2, "2026-06-03", "PAYROLL", "REF-002", "0", "20000");
        });

        ParsedFile parsed = parser.parseSettlementReport(file);

        assertThat(parsed.getRowErrors()).isEmpty();
        assertThat(parsed.getRows()).hasSize(2);
        ParsedRow first = parsed.getRows().get(0);
        assertThat(first.getTransactionDate()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(first.getNarration()).isEqualTo("CARD SETTLEMENT");
        assertThat(first.getReferenceNumber()).isEqualTo("REF-001");
        assertThat(first.getCredit()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void parseSettlementReport_rowMissingNarration_capturesRowErrorWithoutFailingOtherRows() {
        byte[] file = workbook(sheet -> {
            writeSettlementHeaderRow(sheet, 0);
            writeSettlementDataRow(sheet, 1, "2026-06-02", "", "REF-001", "0", "5000");
            writeSettlementDataRow(sheet, 2, "2026-06-03", "PAYROLL", "REF-002", "0", "20000");
        });

        ParsedFile parsed = parser.parseSettlementReport(file);

        assertThat(parsed.getRows()).hasSize(1);
        assertThat(parsed.getRowErrors()).hasSize(1);
        assertThat(parsed.getRowErrors().get(0).getMessage()).contains("Narration");
    }

    private void writeHeaderRow(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void writeTableHeader(Sheet sheet, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        String[] columns = {"Transaction Date", "Value Date", "Narration", "Reference Number", "Debit", "Credit", "Balance"};
        for (int i = 0; i < columns.length; i++) {
            row.createCell(i).setCellValue(columns[i]);
        }
    }

    private void writeDataRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private void writeSettlementHeaderRow(Sheet sheet, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        String[] columns = {"Transaction Date", "Narration", "Transaction Reference", "Debit", "Credit"};
        for (int i = 0; i < columns.length; i++) {
            row.createCell(i).setCellValue(columns[i]);
        }
    }

    private void writeSettlementDataRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private byte[] workbook(Consumer<Sheet> sheetBuilder) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Statement");
            sheetBuilder.accept(sheet);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
