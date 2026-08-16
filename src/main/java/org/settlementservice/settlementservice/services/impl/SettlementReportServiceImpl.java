package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.async.SettlementReportUploadTask;
import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.exceptions.ValidationException;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.enums.ReconciliationStatus;
import org.settlementservice.settlementservice.models.ReconciliationFormula;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.reconciliation.utils.ReconciliationReferenceEvaluator;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.SettlementReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementReportServiceImpl implements SettlementReportService {

    private final SettlementReportRepository settlementReportRepository;
    private final TransactionRepository transactionRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final ReconciliationFormulaRepository reconciliationFormulaRepository;
    private final ModelMapper modelMapper;
    private final SettlementReportUploadTask settlementReportUploadTask;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Override
    public SettlementReportUploadResponse upload(Long transactionId, MultipartFile file, Long formulaId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] fileBytes = readBytes(file);

        // Only the DB read+write is wrapped explicitly — the async trigger below must run after
        // this has committed, not from inside a method-level @Transactional that wouldn't commit
        // until the whole method (including the trigger) returns.
        SettlementReport saved = transactionTemplate.execute(status -> {
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("No transaction found with id " + transactionId));

            // Check if a settlement report already exists for this transaction
            settlementReportRepository.findByTransactionId(transactionId).ifPresent(existingReport -> {
                // If the existing report failed, delete it to allow re-upload
                if (existingReport.getStatus() == BatchStatus.FAILED) {
                    log.info("Deleting failed settlement report {} for transaction {} to allow re-upload",
                            existingReport.getId(), transactionId);

                    try {
                        // Delete cascade: discrepancies → settlement transactions → settlement report
                        // 1. Delete all discrepancies linked to this report's settlement transactions
                        discrepancyRepository.deleteBySettlementReportId(existingReport.getId());
                        log.info("Deleted discrepancies for report {}", existingReport.getId());

                        // 2. Delete all settlement transactions
                        settlementTransactionRepository.deleteBySettlementReportId(existingReport.getId());
                        log.info("Deleted settlement transactions for report {}", existingReport.getId());

                        // 3. Flush the session to ensure the @Modifying deletes are applied
                        entityManager.flush();
                        log.info("Flushed EntityManager after deletes");

                        // 4. Delete the settlement report
                        settlementReportRepository.delete(existingReport);
                        entityManager.flush();
                        log.info("Successfully deleted failed settlement report {}", existingReport.getId());
                    } catch (Exception e) {
                        log.error("Failed to delete settlement report {}", existingReport.getId(), e);
                        throw new RuntimeException("Failed to delete failed settlement report: " + e.getMessage(), e);
                    }
                } else {
                    // Report exists and is not failed (PENDING, PROCESSING, or COMPLETED)
                    throw new DuplicateResourceException(
                            "A settlement report already exists for transaction " + transactionId +
                            " with status " + existingReport.getStatus() + ". Cannot upload a new report.");
                }
            });

            SettlementReport settlementReport = new SettlementReport();
            settlementReport.setTransaction(transaction);
            settlementReport.setAccount(transaction.getAccount());
            settlementReport.setFileName(file.getOriginalFilename());
            settlementReport.setUploadDate(Instant.now());
            settlementReport.setStatus(BatchStatus.PENDING);

            // If formulaId is provided, validate and set it
            if (formulaId != null) {
                ReconciliationFormula formula = reconciliationFormulaRepository.findById(formulaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Reconciliation formula not found: " + formulaId));

                // Validate formula belongs to same account as transaction
                if (!formula.getAccount().getId().equals(transaction.getAccount().getId())) {
                    throw new ValidationException("Formula belongs to a different account");
                }

                settlementReport.setReconciliationFormula(formula);
            }

            return settlementReportRepository.save(settlementReport);
        });

        settlementReportUploadTask.process(saved.getId(), saved.getFileName(), fileBytes);
        return modelMapper.map(saved, SettlementReportUploadResponse.class);
    }

    @Override
    public SettlementReportUploadResponse getStatus(Long id) {
        SettlementReport settlementReport = settlementReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement report not found: " + id));
        return modelMapper.map(settlementReport, SettlementReportUploadResponse.class);
    }

    @Override
    @Transactional
    public void updateReconciliationFormula(Long reportId, Long formulaId) {
        // Fetch settlement report
        SettlementReport report = settlementReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement report not found: " + reportId));

        // Fetch new formula
        ReconciliationFormula formula = reconciliationFormulaRepository.findById(formulaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation formula not found: " + formulaId));

        // Validate formula belongs to same account as report
        if (!formula.getAccount().getId().equals(report.getAccount().getId())) {
            throw new ValidationException("Formula belongs to a different account");
        }

        // Update report's formula
        report.setReconciliationFormula(formula);
        settlementReportRepository.save(report);

        // Reset all settlement transactions to PENDING and recompute references
        List<SettlementTransaction> transactions = settlementTransactionRepository.findBySettlementReportId(reportId);
        for (SettlementTransaction transaction : transactions) {
            transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            String reference = ReconciliationReferenceEvaluator.evaluate(formula.getFormula(), transaction);
            transaction.setReconciliationReference(reference);
        }
        settlementTransactionRepository.saveAll(transactions);

        log.info("Updated reconciliation formula for settlement report {} to formula {} ({}). Reset {} transactions to PENDING.",
                reportId, formulaId, formula.getName(), transactions.size());
    }

    @Override
    public SettlementReportUploadResponse getByTransactionId(Long transactionId) {
        SettlementReport settlementReport = settlementReportRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("No settlement report found for transaction: " + transactionId));
        return modelMapper.map(settlementReport, SettlementReportUploadResponse.class);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
