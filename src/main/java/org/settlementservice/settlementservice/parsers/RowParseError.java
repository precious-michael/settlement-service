package org.settlementservice.settlementservice.parsers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RowParseError {

    private final int rowNumber;
    private final String rawRow;
    private final String message;

}
