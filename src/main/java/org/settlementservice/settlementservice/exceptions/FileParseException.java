package org.settlementservice.settlementservice.exceptions;

import org.settlementservice.settlementservice.parsers.RowParseError;

/**
 * Thrown when the uploaded file itself is unreadable (corrupt bytes, wrong format,
 * missing required structure) — as opposed to a single bad row, which is captured
 * as a {@link RowParseError} instead of failing the whole file.
 */
public class FileParseException extends RuntimeException {

    public FileParseException(String message) {
        super(message);
    }

    public FileParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
