package org.settlementservice.settlementservice.parsers;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.settlementservice.settlementservice.exceptions.FileParseException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads .csv files for either upload type. A bank statement follows the same header-block-then-
 * table contract as {@link ExcelStatementFileParser}'s bank statement parsing (a row whose first
 * field reads "Transaction Date" marks the table start; the header block above it — statement
 * period, balances — is skipped without being parsed), read positionally since there's no single
 * consistent header. A settlement report is addressed by header name instead (column order in
 * the file doesn't matter): transaction_date, narration, transaction_reference, debit, credit are
 * required; settlement_date, rrn, stan, terminal_id are read when present but optional.
 */
@Component
public class CsvStatementFileParser implements StatementFileParser {

    private static final String TABLE_HEADER_LABEL = "transaction date";
    private static final Set<String> REQUIRED_SETTLEMENT_HEADERS =
            Set.of("transaction_date", "narration", "transaction_reference", "debit", "credit");

    @Override
    public ParsedFile parseBankStatement(byte[] fileBytes) {
        // ignoreEmptyLines defaults to true in commons-csv, which would make a blank line
        // between rows invisible rather than a record we can detect — disable it so a blank
        // row terminates the table the same way it does for the Excel parser.
        CSVFormat format = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(false).build();

        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8), format)) {
            List<CSVRecord> records = parser.getRecords();

            int tableHeaderRowIndex = findTableHeaderRowIndex(records);
            if (tableHeaderRowIndex < 0) {
                throw new FileParseException(
                        "Could not find a 'Transaction Date' table header row in the uploaded file");
            }

            List<ParsedRow> rows = new ArrayList<>();
            List<RowParseError> rowErrors = new ArrayList<>();

            for (int i = tableHeaderRowIndex + 1; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                if (isBlankRecord(record)) {
                    break;
                }
                int rowNumber = i + 1;
                try {
                    rows.add(parseTransactionRow(rowNumber, record));
                } catch (RuntimeException e) {
                    rowErrors.add(new RowParseError(rowNumber, record.toString(), e.getMessage()));
                }
            }

            return new ParsedFile(rows, rowErrors);
        } catch (IOException e) {
            throw new FileParseException("Uploaded file is not a readable CSV", e);
        } catch (FileParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FileParseException("Uploaded file is not a readable CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public ParsedFile parseSettlementReport(byte[] fileBytes) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8), format)) {
            if (!parser.getHeaderNames().stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet())
                    .containsAll(REQUIRED_SETTLEMENT_HEADERS)) {
                throw new FileParseException(
                        "CSV is missing one of the required columns: " + REQUIRED_SETTLEMENT_HEADERS);
            }

            List<ParsedRow> rows = new ArrayList<>();
            List<RowParseError> rowErrors = new ArrayList<>();
            int rowNumber = 1;
            for (CSVRecord record : parser) {
                rowNumber++;
                try {
                    rows.add(parseSettlementRow(rowNumber, record));
                } catch (RuntimeException e) {
                    rowErrors.add(new RowParseError(rowNumber, record.toString(), e.getMessage()));
                }
            }
            return new ParsedFile(rows, rowErrors);
        } catch (IOException e) {
            throw new FileParseException("Uploaded file is not a readable CSV", e);
        } catch (FileParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FileParseException("Uploaded file is not a readable CSV: " + e.getMessage(), e);
        }
    }

    private int findTableHeaderRowIndex(List<CSVRecord> records) {
        for (int i = 0; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            String label = fieldAt(record, 0);
            if (label != null && label.equalsIgnoreCase(TABLE_HEADER_LABEL)) {
                return i;
            }
        }
        return -1;
    }

    private ParsedRow parseTransactionRow(int rowNumber, CSVRecord record) {
        LocalDate transactionDate = requireDate(fieldAt(record, 0), "Transaction Date");
        LocalDate valueDate = parseDate(fieldAt(record, 1));
        String narration = fieldAt(record, 2);
        String referenceNumber = fieldAt(record, 3);
        BigDecimal debit = orZero(parseDecimal(fieldAt(record, 4), "Debit"));
        BigDecimal credit = orZero(parseDecimal(fieldAt(record, 5), "Credit"));
        BigDecimal balance = parseDecimal(fieldAt(record, 6), "Balance");
        return new ParsedRow(rowNumber, transactionDate, valueDate, narration, referenceNumber,
                debit, credit, balance, null, null, null, null);
    }

    private ParsedRow parseSettlementRow(int rowNumber, CSVRecord record) {
        LocalDate transactionDate = requireDate(get(record, "transaction_date"), "transaction_date");
        String narration = requireText(get(record, "narration"), "narration");
        String referenceNumber = requireText(get(record, "transaction_reference"), "transaction_reference");
        BigDecimal debit = orZero(parseDecimal(get(record, "debit"), "debit"));
        BigDecimal credit = orZero(parseDecimal(get(record, "credit"), "credit"));
        LocalDate settlementDate = parseDate(get(record, "settlement_date"));
        String rrn = get(record, "rrn");
        String stan = get(record, "stan");
        String terminalId = get(record, "terminal_id");
        return new ParsedRow(rowNumber, transactionDate, null, narration, referenceNumber,
                debit, credit, null, settlementDate, rrn, stan, terminalId);
    }

    private String fieldAt(CSVRecord record, int index) {
        if (index >= record.size()) {
            return null;
        }
        String value = record.get(index);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String get(CSVRecord record, String header) {
        String value = record.isMapped(header) ? record.get(header) : null;
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String requireText(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private LocalDate requireDate(String value, String label) {
        LocalDate date = parseDate(value);
        if (date == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return date;
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable date value: '" + value + "'");
        }
    }

    private BigDecimal parseDecimal(String value, String label) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " has an unparseable numeric value: '" + value + "'");
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
