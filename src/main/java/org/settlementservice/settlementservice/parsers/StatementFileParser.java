package org.settlementservice.settlementservice.parsers;

/**
 * A file-format-specific parser capable of reading both upload types — implementations are
 * chosen by file extension via {@link StatementFileParserFactory}, not by upload type, so either
 * a bank statement or a settlement report may be uploaded as either .xlsx or .csv.
 */
public interface StatementFileParser {

    ParsedFile parseBankStatement(byte[] fileBytes);

    ParsedFile parseSettlementReport(byte[] fileBytes);
}
