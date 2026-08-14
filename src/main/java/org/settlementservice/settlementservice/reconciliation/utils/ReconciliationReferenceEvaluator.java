package org.settlementservice.settlementservice.reconciliation.utils;

import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.models.SettlementTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates reconciliation reference formulas by replacing ${field} placeholders with actual values.
 * Formula examples: "${rrn}/${stan}", "${cid}/${referenceNumber}", "${terminalId}|${transactionDate}"
 */
public class ReconciliationReferenceEvaluator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");

    /**
     * Evaluates a formula template by replacing ${field} placeholders with values from the map.
     *
     * @param formula the template (e.g., "${rrn}/${stan}")
     * @param fieldValues map of field name -> value
     * @return the evaluated string, or null if any required field is missing
     */
    public static String evaluate(String formula, Map<String, String> fieldValues) {
        if (formula == null || formula.isEmpty()) {
            return null;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(formula);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String value = fieldValues.get(fieldName);

            if (value == null) {
                return null;
            }

            result.append(formula, lastEnd, matcher.start());
            result.append(value);
            lastEnd = matcher.end();
        }

        result.append(formula.substring(lastEnd));
        return result.toString();
    }

    /**
     * Evaluates a formula using field values from a SettlementTransaction.
     */
    public static String evaluate(String formula, SettlementTransaction transaction) {
        if (formula == null || transaction == null) {
            return null;
        }

        Map<String, String> fields = new HashMap<>();
        addIfNotNull(fields, "rrn", transaction.getRrn());
        addIfNotNull(fields, "stan", transaction.getStan());
        addIfNotNull(fields, "terminalId", transaction.getTerminalId());
        addIfNotNull(fields, "transactionReference", transaction.getTransactionReference());
        addIfNotNull(fields, "transactionDate", transaction.getTransactionDate() != null
                ? transaction.getTransactionDate().toString() : null);
        addIfNotNull(fields, "narration", transaction.getNarration());

        return evaluate(formula, fields);
    }

    /**
     * Evaluates a formula using field values from an InternalRecord.
     */
    public static String evaluate(String formula, InternalRecord record) {
        if (formula == null || record == null) {
            return null;
        }

        Map<String, String> fields = new HashMap<>();
        addIfNotNull(fields, "rrn", record.getRrn());
        addIfNotNull(fields, "stan", record.getStan());
        addIfNotNull(fields, "terminalId", record.getTerminalId());
        addIfNotNull(fields, "referenceNumber", record.getReferenceNumber());
        addIfNotNull(fields, "transactionDate", record.getTransactionDate() != null
                ? record.getTransactionDate().toString() : null);
        addIfNotNull(fields, "narration", record.getNarration());
        addIfNotNull(fields, "productType", record.getProductType());

        return evaluate(formula, fields);
    }

    private static void addIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }
}
