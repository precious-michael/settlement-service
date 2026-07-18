package org.settlementservice.settlementservice.parsers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatementFileParserFactory {

    private final CsvStatementFileParser csvStatementFileParser;
    private final ExcelStatementFileParser excelStatementFileParser;

    public StatementFileParser getParser(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename must not be null");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) {
            return csvStatementFileParser;
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return excelStatementFileParser;
        } else {
            throw new IllegalArgumentException("Unsupported file type. Only .csv, .xlsx, and .xls files are accepted.");
        }
    }
}
