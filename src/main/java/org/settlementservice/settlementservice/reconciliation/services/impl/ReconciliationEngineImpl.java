package org.settlementservice.settlementservice.reconciliation.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationRunResponse;
import org.settlementservice.settlementservice.enums.ReconciliationStatus;
import org.settlementservice.settlementservice.enums.ReportReconciliationStatus;
import org.settlementservice.settlementservice.models.Discrepancy;
import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.models.ReconciliationFormula;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.repositories.InternalRecordRepository;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.reconciliation.services.ReconciliationEngine;
import org.settlementservice.settlementservice.reconciliation.utils.ReconciliationReferenceEvaluator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reconciliation engine that matches settlement transactions to internal records
 * using formula-based matching strategies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEngineImpl implements ReconciliationEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");

    private final SettlementTransactionRepository settlementTransactionRepository;
    private final SettlementReportRepository settlementReportRepository;
    private final InternalRecordRepository internalRecordRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final ReconciliationFormulaRepository reconciliationFormulaRepository;

    @Override
    public ReconciliationRunResponse run() {
        // Process both PENDING (new) and MISSING (retry after internal records added)
        List<SettlementTransaction> pendingTransactions = settlementTransactionRepository
                .findByReconciliationStatus(ReconciliationStatus.PENDING);
        List<SettlementTransaction> missingTransactions = settlementTransactionRepository
                .findByReconciliationStatus(ReconciliationStatus.MISSING);

        List<SettlementTransaction> pending = new java.util.ArrayList<>();
        pending.addAll(pendingTransactions);
        pending.addAll(missingTransactions);

        if (pending.isEmpty()) {
            log.info("No pending or missing transactions to reconcile");
            return ReconciliationRunResponse.builder()
                    .totalProcessed(0)
                    .matched(0)
                    .mismatched(0)
                    .noMatchFound(0)
                    .build();
        }

        log.info("Reconciling {} transactions ({} pending, {} missing)",
                pending.size(), pendingTransactions.size(), missingTransactions.size());

        // Group transactions by account for parallel processing
        Map<Long, List<SettlementTransaction>> transactionsByAccount = pending.stream()
                .collect(Collectors.groupingBy(tx -> tx.getSettlementReport().getAccount().getId()));

        log.info("Reconciling {} transactions across {} accounts in parallel",
                pending.size(), transactionsByAccount.size());

        // Process each account's transactions in parallel
        // Each account saves its own results immediately as it completes
        List<CompletableFuture<AccountReconciliationResult>> futures = transactionsByAccount.entrySet().stream()
                .map(entry -> reconcileAccountTransactionsAsync(entry.getKey(), entry.getValue()))
                .toList();

        // Wait for all accounts to complete and aggregate counts
        // Note: Each account has already saved its results independently
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Aggregate only the counts (data is already saved)
        int totalReconciled = 0, totalUnreconciled = 0, totalMissing = 0;

        for (CompletableFuture<AccountReconciliationResult> future : futures) {
            AccountReconciliationResult result = future.join();
            totalReconciled += result.reconciled;
            totalUnreconciled += result.unreconciled;
            totalMissing += result.missing;
        }

        log.info("Reconciliation run complete — processed={} reconciled={} unreconciled={} missing={}",
                pending.size(), totalReconciled, totalUnreconciled, totalMissing);

        return ReconciliationRunResponse.builder()
                .totalProcessed(pending.size())
                .matched(totalReconciled)
                .mismatched(totalUnreconciled)
                .noMatchFound(totalMissing)
                .build();
    }

    /**
     * Processes reconciliation for a single account's transactions asynchronously.
     * Runs in parallel with other accounts using the "reconciliation" thread pool.
     * Each account saves its own results immediately in its own transaction.
     * This ensures that if one account fails, other accounts' work is not lost.
     *
     * @param accountId Account ID for logging
     * @param transactions List of pending transactions for this account
     * @return CompletableFuture containing reconciliation counts for this account
     */
    @Async("reconciliation")
    @Transactional
    public CompletableFuture<AccountReconciliationResult> reconcileAccountTransactionsAsync(
            Long accountId, List<SettlementTransaction> transactions) {

        log.debug("Starting reconciliation for account {} with {} transactions",
                accountId, transactions.size());

        int reconciled = 0, unreconciled = 0, missing = 0;
        List<SettlementTransaction> transactionsToUpdate = new ArrayList<>();
        List<Discrepancy> discrepancies = new ArrayList<>();

        for (SettlementTransaction transaction : transactions) {
            // Get the formula from the settlement report
            ReconciliationFormula formula = transaction.getSettlementReport().getReconciliationFormula();

            if (formula == null) {
                // No formula set - try to use account's default
                formula = reconciliationFormulaRepository
                        .findByAccountIdAndIsDefaultTrue(accountId)
                        .orElse(null);

                if (formula == null) {
                    log.warn("SettlementTransaction {} skipped — no reconciliation formula available",
                            transaction.getId());
                    transaction.setReconciliationStatus(ReconciliationStatus.MISSING);
                    transactionsToUpdate.add(transaction);
                    missing++;
                    continue;
                }
            }

            // Find matching internal record using the formula
            Optional<InternalRecord> internalOpt = findMatchingInternalRecord(transaction, formula);

            if (internalOpt.isEmpty()) {
                log.debug("SettlementTransaction {} MISSING — no InternalRecord found using formula '{}'",
                        transaction.getId(), formula.getName());
                transaction.setReconciliationStatus(ReconciliationStatus.MISSING);
                transactionsToUpdate.add(transaction);
                missing++;
                continue;
            }

            InternalRecord internal = internalOpt.get();
            BigDecimal settlementNet = transaction.getCredit().subtract(transaction.getDebit());
            BigDecimal internalNet = internal.getCredit().subtract(internal.getDebit());

            if (settlementNet.compareTo(internalNet) == 0) {
                transaction.setReconciliationStatus(ReconciliationStatus.RECONCILED);
                log.debug("SettlementTransaction {} RECONCILED — net={}", transaction.getId(), settlementNet);
                reconciled++;
            } else {
                transaction.setReconciliationStatus(ReconciliationStatus.UNRECONCILED);
                BigDecimal absoluteDifference = internalNet.subtract(settlementNet).abs();

                Discrepancy discrepancy = new Discrepancy();
                discrepancy.setTransaction(transaction.getSettlementReport().getTransaction());
                discrepancy.setSettlementTransaction(transaction);

                // Keep old fields for backward compatibility
                discrepancy.setExpectedAmount(internalNet);
                discrepancy.setReportedAmount(settlementNet);

                // New detailed breakdown - separate debit/credit
                discrepancy.setExpectedDebit(internal.getDebit());
                discrepancy.setExpectedCredit(internal.getCredit());
                discrepancy.setReportedDebit(transaction.getDebit());
                discrepancy.setReportedCredit(transaction.getCredit());

                // Absolute difference
                discrepancy.setDifference(absoluteDifference);

                // What fields were used to match
                discrepancy.setMatchedOn(buildMatchedOnString(transaction, internal, formula));

                discrepancies.add(discrepancy);

                log.warn("SettlementTransaction {} UNRECONCILED — settlement={} internal={} diff={}",
                        transaction.getId(), settlementNet, internalNet, absoluteDifference);
                unreconciled++;
            }
            transactionsToUpdate.add(transaction);
        }

        // Save this account's results immediately (in this thread's transaction)
        // This ensures work is not lost if other accounts fail
        if (!transactionsToUpdate.isEmpty()) {
            settlementTransactionRepository.saveAll(transactionsToUpdate);
        }
        if (!discrepancies.isEmpty()) {
            discrepancyRepository.saveAll(discrepancies);
        }

        // Update settlement report reconciliation statuses
        updateSettlementReportStatuses(transactionsToUpdate);

        log.debug("Completed and saved reconciliation for account {} — reconciled={} unreconciled={} missing={}",
                accountId, reconciled, unreconciled, missing);

        // Return only counts (data already saved)
        return CompletableFuture.completedFuture(
                new AccountReconciliationResult(reconciled, unreconciled, missing));
    }

    /**
     * Updates each settlement report's reconciliation status based on its transactions.
     * - RECONCILED: All transactions are RECONCILED
     * - UNRECONCILED: All transactions are UNRECONCILED or MISSING (none RECONCILED)
     * - PARTIAL_RECONCILIATION: Mix of RECONCILED and other statuses
     * - PENDING: All transactions still PENDING or MISSING (not yet reconciled)
     */
    private void updateSettlementReportStatuses(List<SettlementTransaction> transactions) {
        // Group transactions by settlement report
        Map<Long, List<SettlementTransaction>> byReport = transactions.stream()
                .collect(Collectors.groupingBy(tx -> tx.getSettlementReport().getId()));

        byReport.forEach((reportId, reportTransactions) -> {
            // Count statuses
            long total = reportTransactions.size();
            long reconciled = reportTransactions.stream()
                    .filter(tx -> tx.getReconciliationStatus() == ReconciliationStatus.RECONCILED)
                    .count();
            long unreconciled = reportTransactions.stream()
                    .filter(tx -> tx.getReconciliationStatus() == ReconciliationStatus.UNRECONCILED)
                    .count();
            long pending = reportTransactions.stream()
                    .filter(tx -> tx.getReconciliationStatus() == ReconciliationStatus.PENDING)
                    .count();
            long missing = reportTransactions.stream()
                    .filter(tx -> tx.getReconciliationStatus() == ReconciliationStatus.MISSING)
                    .count();

            // Determine report status
            ReportReconciliationStatus reportStatus;
            if (reconciled == total) {
                // All reconciled
                reportStatus = ReportReconciliationStatus.RECONCILED;
            } else if (reconciled == 0 && (unreconciled + missing == total)) {
                // All unreconciled/missing (none reconciled)
                reportStatus = ReportReconciliationStatus.UNRECONCILED;
            } else if (reconciled > 0) {
                // Some reconciled, some not
                reportStatus = ReportReconciliationStatus.PARTIAL_RECONCILIATION;
            } else {
                // Not yet reconciled (all pending)
                reportStatus = ReportReconciliationStatus.PENDING;
            }

            // Update the settlement report
            settlementReportRepository.findById(reportId).ifPresent(report -> {
                report.setReconciliationStatus(reportStatus);
                settlementReportRepository.save(report);
            });
        });
    }

    /**
     * Finds a matching InternalRecord for the given SettlementTransaction using the formula.
     * Parses the formula to determine which fields to match on, then queries accordingly.
     */
    private Optional<InternalRecord> findMatchingInternalRecord(SettlementTransaction transaction,
                                                                  ReconciliationFormula formula) {
        String formulaTemplate = formula.getFormula();

        // Extract field names from formula
        List<String> fields = extractFieldNames(formulaTemplate);

        // Try to match using the most common field combinations
        // This is more efficient than building dynamic queries
        // Using findFirst to handle duplicate internal records gracefully
        if (fields.contains("rrn") && fields.contains("stan")) {
            return internalRecordRepository.findFirstByRrnAndStan(transaction.getRrn(), transaction.getStan());
        } else if (fields.contains("rrn")) {
            return internalRecordRepository.findFirstByRrn(transaction.getRrn());
        } else if (fields.contains("terminalId") && fields.contains("transactionDate")) {
            return internalRecordRepository.findFirstByTerminalIdAndTransactionDate(
                    transaction.getTerminalId(), transaction.getTransactionDate());
        } else if (fields.contains("referenceNumber") || fields.contains("transactionReference")) {
            return internalRecordRepository.findFirstByReferenceNumber(transaction.getTransactionReference());
        }

        // Fallback: compute reference and try exact match
        // (This is slower but handles any formula)
        String settlementRef = ReconciliationReferenceEvaluator.evaluate(formulaTemplate, transaction);
        if (settlementRef == null) {
            return Optional.empty();
        }

        return internalRecordRepository.findAll().stream()
                .filter(internal -> {
                    String internalRef = ReconciliationReferenceEvaluator.evaluate(formulaTemplate, internal);
                    return settlementRef.equals(internalRef);
                })
                .findFirst();
    }

    /**
     * Extracts field names from a formula template.
     * Example: "${rrn}/${stan}" → ["rrn", "stan"]
     */
    private List<String> extractFieldNames(String formula) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(formula);
        return matcher.results()
                .map(result -> result.group(1))
                .toList();
    }

    /**
     * Builds a human-readable string showing what fields were used to match the records.
     * Example: "RRN: ABC123, STAN: 456789" or "Terminal: TERM001, Date: 2024-08-13"
     */
    private String buildMatchedOnString(SettlementTransaction settlement, InternalRecord internal,
                                       ReconciliationFormula formula) {
        List<String> fields = extractFieldNames(formula.getFormula());
        List<String> parts = new ArrayList<>();

        for (String field : fields) {
            String settlementValue = getFieldValue(settlement, field);
            String internalValue = getFieldValue(internal, field);

            // Use settlement value if available, otherwise internal
            String value = settlementValue != null ? settlementValue : internalValue;

            if (value != null && !value.isEmpty()) {
                String displayName = formatFieldName(field);
                parts.add(displayName + ": " + value);
            }
        }

        return parts.isEmpty() ? "Unknown match criteria" : String.join(", ", parts);
    }

    /**
     * Gets field value from settlement transaction or internal record using reflection-free approach.
     */
    private String getFieldValue(Object obj, String fieldName) {
        if (obj instanceof SettlementTransaction st) {
            return switch (fieldName.toLowerCase()) {
                case "rrn" -> st.getRrn();
                case "stan" -> st.getStan();
                case "terminalid" -> st.getTerminalId();
                case "transactionreference", "referencenumber" -> st.getTransactionReference();
                case "transactiondate" -> st.getTransactionDate() != null ? st.getTransactionDate().toString() : null;
                default -> null;
            };
        } else if (obj instanceof InternalRecord ir) {
            return switch (fieldName.toLowerCase()) {
                case "rrn" -> ir.getRrn();
                case "stan" -> ir.getStan();
                case "terminalid" -> ir.getTerminalId();
                case "transactionreference", "referencenumber" -> ir.getReferenceNumber();
                case "transactiondate" -> ir.getTransactionDate() != null ? ir.getTransactionDate().toString() : null;
                default -> null;
            };
        }
        return null;
    }

    /**
     * Formats field names for display.
     */
    private String formatFieldName(String field) {
        return switch (field.toLowerCase()) {
            case "rrn" -> "RRN";
            case "stan" -> "STAN";
            case "terminalid" -> "Terminal ID";
            case "transactionreference", "referencenumber" -> "Reference";
            case "transactiondate" -> "Transaction Date";
            default -> field;
        };
    }

    /**
     * Holds reconciliation counts for a single account.
     * Used to aggregate counts from parallel account processing.
     * Note: The actual data is already saved by each async method in its own transaction.
     */
    private record AccountReconciliationResult(
            int reconciled,
            int unreconciled,
            int missing
    ) {}
}

