package org.settlementservice.settlementservice.parsers;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.settlementservice.settlementservice.exceptions.FileParseException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads .xlsx files for either upload type. A bank statement has a header block (label/value
 * rows, e.g. statement period and balances) before the data table — that block is skipped
 * without being parsed, and the table itself starts at the row whose first cell reads
 * "Transaction Date", with fixed columns: Transaction Date, Value Date, Narration, Reference
 * Number, Debit, Credit, Balance. A settlement report is flatter: row 0 is a label row (not used
 * for lookup — purely documentation, same as the bank statement table is positional), then fixed
 * columns: Transaction Date, Narration, Transaction Reference, Debit, Credit. Only the first
 * sheet is read.
 */
@Component
public class ExcelStatementFileParser implements StatementFileParser {

    private static final String TABLE_HEADER_LABEL = "transaction date";

    @Override
    public ParsedFile parseBankStatement(byte[] fileBytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            int tableHeaderRowIndex = findTableHeaderRowIndex(sheet);
            if (tableHeaderRowIndex < 0) {
                throw new FileParseException(
                        "Could not find a 'Transaction Date' table header row in the uploaded file");
            }

            List<ParsedRow> rows = new ArrayList<>();
            List<RowParseError> rowErrors = new ArrayList<>();

            int lastRowNum = sheet.getLastRowNum();
            for (int i = tableHeaderRowIndex + 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (isBlankRow(row)) {
                    break;
                }
                int rowNumber = i + 1;
                try {
                    rows.add(parseTransactionRow(rowNumber, row));
                } catch (RuntimeException e) {
                    rowErrors.add(new RowParseError(rowNumber, rawRowText(row), e.getMessage()));
                }
            }

            return new ParsedFile(rows, rowErrors);
        } catch (IOException e) {
            throw new FileParseException("Uploaded file is not a readable Excel workbook", e);
        } catch (FileParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FileParseException("Uploaded file is not a readable Excel workbook: " + e.getMessage(), e);
        }
    }

    @Override
    public ParsedFile parseSettlementReport(byte[] fileBytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<ParsedRow> rows = new ArrayList<>();
            List<RowParseError> rowErrors = new ArrayList<>();

            int lastRowNum = sheet.getLastRowNum();
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (isBlankRow(row)) {
                    break;
                }
                int rowNumber = i + 1;
                try {
                    rows.add(parseSettlementRow(rowNumber, row));
                } catch (RuntimeException e) {
                    rowErrors.add(new RowParseError(rowNumber, rawRowText(row), e.getMessage()));
                }
            }

            return new ParsedFile(rows, rowErrors);
        } catch (IOException e) {
            throw new FileParseException("Uploaded file is not a readable Excel workbook", e);
        } catch (RuntimeException e) {
            throw new FileParseException("Uploaded file is not a readable Excel workbook: " + e.getMessage(), e);
        }
    }

    private int findTableHeaderRowIndex(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String label = cellText(row.getCell(0));
            if (label != null && label.trim().equalsIgnoreCase(TABLE_HEADER_LABEL)) {
                return i;
            }
        }
        return -1;
    }

    private ParsedRow parseTransactionRow(int rowNumber, Row row) {
        LocalDate transactionDate = requireDate(row.getCell(0), "Transaction Date");
        LocalDate valueDate = parseDateCell(row.getCell(1));
        String narration = cellText(row.getCell(2));
        String referenceNumber = cellText(row.getCell(3));
        BigDecimal debit = orZero(parseDecimalCell(row.getCell(4), "Debit"));
        BigDecimal credit = orZero(parseDecimalCell(row.getCell(5), "Credit"));
        BigDecimal balance = parseDecimalCell(row.getCell(6), "Balance");
        return new ParsedRow(rowNumber, transactionDate, valueDate, narration, referenceNumber,
                debit, credit, balance);
    }

    private ParsedRow parseSettlementRow(int rowNumber, Row row) {
        LocalDate transactionDate = requireDate(row.getCell(0), "Transaction Date");
        String narration = requireText(cellText(row.getCell(1)), "Narration");
        String referenceNumber = requireText(cellText(row.getCell(2)), "Transaction Reference");
        BigDecimal debit = orZero(parseDecimalCell(row.getCell(3), "Debit"));
        BigDecimal credit = orZero(parseDecimalCell(row.getCell(4), "Credit"));
        return new ParsedRow(rowNumber, transactionDate, null, narration, referenceNumber,
                debit, credit, null);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private LocalDate requireDate(Cell cell, String label) {
        LocalDate date = parseDateCell(cell);
        if (date == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return date;
    }

    private LocalDate parseDateCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = cellText(cell);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable date value: '" + text + "'");
        }
    }

    private BigDecimal parseDecimalCell(Cell cell, String label) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String text = cellText(cell);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " has an unparseable numeric value: '" + text + "'");
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            String text = cellText(cell);
            if (text != null && !text.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String rawRowText(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            if (c > 0) {
                sb.append(", ");
            }
            String text = cellText(row.getCell(c));
            sb.append(text != null ? text : "");
        }
        return sb.toString();
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double value = cell.getNumericCellValue();
                yield (value == Math.floor(value) && !Double.isInfinite(value))
                        ? BigDecimal.valueOf(value).toBigInteger().toString()
                        : BigDecimal.valueOf(value).toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
