package org.settlementservice.settlementservice.exceptions;

/**
 * Exception thrown when validation fails.
 */
public class ValidationException extends SettlementServiceException {

    public ValidationException(String message) {
        super(message);
    }
}
