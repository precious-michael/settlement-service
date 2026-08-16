package org.settlementservice.settlementservice.validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates reconciliation formula templates.
 * Ensures all ${field} placeholders reference valid fields.
 */
public class ReconciliationFormulaValidator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");

    /**
     * Valid field names that can be used in reconciliation formulas.
     * These correspond to fields available in both SettlementTransaction and InternalRecord.
     */
    private static final Set<String> VALID_FIELDS = Set.of(
            // Transaction identifiers
            "rrn",
            "stan",
            "terminalId",
            "referenceNumber",
            "transactionReference",
//            "pan",
//            "processorReference",
//            "sessionId",

            // Dates and times
            "transactionDate",
            "transactionTime",

            // Transaction details
            "transactionType",
            "productType",
            "narration"

//            // Account details
//            "sourceAccountNumber",
//            "destinationAccountNumber",
//            "sourceBankCode",
//            "destinationBankCode",

//            // Amounts
//            "amount",
//            "debit",
//            "credit",
//            "currency",

            // Additional
//            "cardAcceptorId"
    );

    /**
     * Validates a reconciliation formula.
     *
     * @param formula the formula template to validate
     * @return validation result with error messages if invalid
     */
    public static ValidationResult validate(String formula) {
        List<String> errors = new ArrayList<>();

        if (formula == null || formula.trim().isEmpty()) {
            errors.add("Formula cannot be null or empty");
            return new ValidationResult(false, errors);
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(formula);
        boolean hasPlaceholders = false;

        while (matcher.find()) {
            hasPlaceholders = true;
            String fieldName = matcher.group(1);

            if (!VALID_FIELDS.contains(fieldName)) {
                errors.add("Invalid field placeholder: ${" + fieldName + "}. Valid fields: " +
                        String.join(", ", VALID_FIELDS));
            }
        }

        if (!hasPlaceholders) {
            errors.add("Formula must contain at least one ${field} placeholder");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Result of formula validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}
