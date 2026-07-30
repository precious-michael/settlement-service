package org.settlementservice.settlementservice.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementReportRowError;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.parsers.ParsedFile;
import org.settlementservice.settlementservice.parsers.ParsedRow;
import org.settlementservice.settlementservice.parsers.RowParseError;
import org.settlementservice.settlementservice.parsers.StatementFileParserFactory;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRowErrorRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
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
            BigDecimal expectedAmount = settlementReport.getTransaction().getCredit()
                    .subtract(settlementReport.getTransaction().getDebit());
            BigDecimal reportedAmount = parsed.getRows().stream()
                    .map(row -> row.getCredit().subtract(row.getDebit()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (expectedAmount.subtract(reportedAmount).abs().compareTo(tolerance) > 0) {
                log.warn("Settlement report {} rejected — expected={} reported={} exceeds tolerance={}",
                        settlementReport.getId(), expectedAmount, reportedAmount, tolerance);
                settlementReportRepository.delete(settlementReport);
                return;
            }
        }

        List<SettlementTransaction> toSave = new ArrayList<>();
        for (ParsedRow row : parsed.getRows()) {
            toSave.add(toSettlementTransaction(settlementReport, row));
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
