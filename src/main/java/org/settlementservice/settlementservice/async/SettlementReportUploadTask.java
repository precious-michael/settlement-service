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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an uploaded settlement report and persists its lines in the background — same shape as
 * {@link BankStatementUploadTask} (see its class comment for the triggering/failure-handling
 * rationale), without classification or dedup since a settlement report can legitimately have
 * several lines per transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementReportUploadTask {

    private final SettlementReportRepository settlementReportRepository;
    private final SettlementReportRowErrorRepository settlementReportRowErrorRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final StatementFileParserFactory statementFileParserFactory;

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

        List<SettlementTransaction> toSave = new ArrayList<>();
        for (ParsedRow row : parsed.getRows()) {
            toSave.add(toSettlementTransaction(settlementReport, row));
        }
        settlementTransactionRepository.saveAll(toSave);

        settlementReport.setTotalEntries(parsed.getRows().size() + parsed.getRowErrors().size());
        settlementReport.setStatus(
                parsed.getRowErrors().isEmpty() ? BatchStatus.COMPLETED : BatchStatus.COMPLETED_WITH_ERRORS);
        settlementReportRepository.save(settlementReport);
    }

    private SettlementTransaction toSettlementTransaction(SettlementReport settlementReport, ParsedRow row) {
        SettlementTransaction settlementTransaction = new SettlementTransaction();
        settlementTransaction.setSettlementReport(settlementReport);
        settlementTransaction.setTransactionDate(row.getTransactionDate());
        settlementTransaction.setNarration(row.getNarration());
        settlementTransaction.setTransactionReference(row.getReferenceNumber());
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
