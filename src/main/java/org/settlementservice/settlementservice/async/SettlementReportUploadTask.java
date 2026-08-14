package org.settlementservice.settlementservice.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.ReconciliationStatus;
import org.settlementservice.settlementservice.models.ReconciliationFormula;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementReportRowError;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.parsers.ParsedFile;
import org.settlementservice.settlementservice.parsers.ParsedRow;
import org.settlementservice.settlementservice.parsers.RowParseError;
import org.settlementservice.settlementservice.parsers.StatementFileParserFactory;
import org.settlementservice.settlementservice.reconciliation.utils.ReconciliationReferenceEvaluator;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRowErrorRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.SettlementValidationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an uploaded settlement report and persists its lines in the background — same shape as
 * {@link BankStatementUploadTask} (see its class comment for the triggering/failure-handling
 * rationale), without classification or dedup since a settlement report can legitimately have
 * several lines per transaction. Unlike a bank statement, a clean parse isn't enough to accept the
 * file: the reported net amount is compared against the linked Transaction's net amount before
 * anything is persisted. Outside {@link #tolerance}, the whole upload is rejected — the
 * already-created {@code SettlementReport} row is deleted (its unique FK to the Transaction would
 * otherwise permanently block a corrected re-upload) and no {@code SettlementTransaction} rows,
 * status change, or {@code Discrepancy} are ever written for it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementReportUploadTask {

    private final SettlementReportRepository settlementReportRepository;
    private final SettlementReportRowErrorRepository settlementReportRowErrorRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationFormulaRepository reconciliationFormulaRepository;
    private final StatementFileParserFactory statementFileParserFactory;
    private final SettlementValidationService settlementValidationService;

    @Value("${reconciliation.tolerance}")
    private BigDecimal tolerance = BigDecimal.ZERO;

    @Async("file-processing")
    public void process(Long settlementReportId, String fileName, byte[] fileBytes) {
        SettlementReport settlementReport = settlementReportRepository.findById(settlementReportId).orElse(null);
        if (settlementReport == null) {
            log.warn("Settlement report {} not found when starting async processing", settlementReportId);
            return;
        }

        settlementReport.setStatus(BatchStatus.PROCESSING);
        settlementReport = settlementReportRepository.save(settlementReport);

        try {
            processFile(settlementReport, fileName, fileBytes);
        } catch (Exception e) {
            log.error("Failed to process settlement report {}", settlementReportId, e);
            settlementReport.setStatus(BatchStatus.FAILED);
            settlementReport.setErrorMessage(e.getMessage());
            settlementReportRepository.save(settlementReport);
        }
    }

    private void processFile(SettlementReport settlementReport, String fileName, byte[] fileBytes) {
        ParsedFile parsed =
                statementFileParserFactory.getParser(fileName).parseSettlementReport(fileBytes);

        for (RowParseError error : parsed.getRowErrors()) {
            saveRowError(settlementReport, error.getRowNumber(), error.getRawRow(), error.getMessage());
        }

        boolean fullyParsed = parsed.getRowErrors().isEmpty();

        // A partial parse skips both the tolerance check and reconciliation below — its reported
        // sum would be unreliable — but the rows that DID parse still get saved, same as before.
        if (fullyParsed) {
            // Fetch transaction by ID to avoid LazyInitializationException
            Long transactionId = settlementReport.getTransaction().getId();
            var transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new IllegalStateException("Transaction not found: " + transactionId));

            BigDecimal expectedAmount = transaction.getCredit().subtract(transaction.getDebit());
            BigDecimal reportedAmount = parsed.getRows().stream()
                    .map(row -> row.getCredit().subtract(row.getDebit()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal difference = expectedAmount.subtract(reportedAmount).abs();
            if (difference.compareTo(tolerance) > 0) {
                String errorMsg = String.format(
                        "Bulk difference rejected: Expected net amount ₦%s, but settlement report shows ₦%s. " +
                        "Difference of ₦%s exceeds tolerance of ₦%s.",
                        expectedAmount, reportedAmount, difference, tolerance);
                log.warn("Settlement report {} rejected — {}", settlementReport.getId(), errorMsg);
                settlementReport.setStatus(BatchStatus.FAILED);
                settlementReport.setErrorMessage(errorMsg);
                settlementReportRepository.save(settlementReport);
                return;
            }
        }

        List<SettlementTransaction> toSave = new ArrayList<>();

        // Fetch formula by ID to avoid LazyInitializationException
        ReconciliationFormula formula = null;
        if (settlementReport.getReconciliationFormula() != null) {
            Long formulaId = settlementReport.getReconciliationFormula().getId();
            formula = reconciliationFormulaRepository.findById(formulaId).orElse(null);
        }

        for (ParsedRow row : parsed.getRows()) {
            SettlementTransaction transaction = toSettlementTransaction(settlementReport, row);

            // If formula is set, compute reconciliation reference immediately
            if (formula != null) {
                String reference = ReconciliationReferenceEvaluator.evaluate(formula.getFormula(), transaction);
                transaction.setReconciliationReference(reference);
                transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            }

            toSave.add(transaction);
        }
        settlementTransactionRepository.saveAll(toSave);

        settlementReport.setTotalEntries(parsed.getRows().size() + parsed.getRowErrors().size());
        settlementReport.setStatus(fullyParsed ? BatchStatus.COMPLETED : BatchStatus.COMPLETED_WITH_ERRORS);
        settlementReportRepository.save(settlementReport);

        if (fullyParsed) {
            settlementValidationService.validateSettlement(settlementReport.getId());
        }
    }

    private SettlementTransaction toSettlementTransaction(SettlementReport settlementReport, ParsedRow row) {
        SettlementTransaction settlementTransaction = new SettlementTransaction();
        settlementTransaction.setSettlementReport(settlementReport);
        settlementTransaction.setTransactionDate(row.getTransactionDate());
        settlementTransaction.setSettlementDate(row.getSettlementDate());
        settlementTransaction.setNarration(row.getNarration());
        settlementTransaction.setTransactionReference(row.getReferenceNumber());
        settlementTransaction.setRrn(row.getRrn());
        settlementTransaction.setStan(row.getStan());
        settlementTransaction.setTerminalId(row.getTerminalId());
        settlementTransaction.setDebit(row.getDebit());
        settlementTransaction.setCredit(row.getCredit());
        return settlementTransaction;
    }

    private void saveRowError(SettlementReport settlementReport, int rowNumber, String rawRow, String message) {
        SettlementReportRowError rowError = new SettlementReportRowError();
        rowError.setSettlementReport(settlementReport);
        rowError.setRowNumber(rowNumber);
        rowError.setRawRow(rawRow);
        rowError.setErrorMessage(message);
        settlementReportRowErrorRepository.save(rowError);
    }
}
