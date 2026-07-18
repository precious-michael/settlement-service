package org.settlementservice.settlementservice.parsers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Result of parsing either upload type: the rows that parsed successfully, and any rows that
 * didn't (captured individually so one bad row doesn't fail the whole file).
 */
@Getter
@Setter
@AllArgsConstructor
public class ParsedFile {

    private final List<ParsedRow> rows;
    private final List<RowParseError> rowErrors;
}
