package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationSummaryResponse;
import org.settlementservice.settlementservice.enums.ReportReconciliationStatus;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.SettlementValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementValidationServiceImpl implements SettlementValidationService {

    private final SettlementReportRepository settlementReportRepository;
    private final TransactionRepository transactionRepository;
    private final DiscrepancyRepository discrepancyRepository;

    @Override
    @Transactional
    public void validateSettlement(Long settlementReportId) {
        SettlementReport report = settlementReportRepository.findById(settlementReportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement report not found: " + settlementReportId));

        Transaction transaction = report.getTransaction();
        transaction.setStatus(TransactionStatus.RESOLVED);
        log.debug("Transaction {} validated and marked RESOLVED", transaction.getId());
        transactionRepository.save(transaction);
    }

    @Override
    public ReconciliationSummaryResponse getSummary(Long accountId, String month) {
        YearMonth yearMonth = parseMonth(month);
        LocalDate dateFrom = yearMonth.atDay(1);
        LocalDate dateTo = yearMonth.atEndOfMonth();

        long total = transactionRepository.countByAccountIdAndTransactionDateBetween(accountId, dateFrom, dateTo);
        long resolved = transactionRepository.countByAccountIdAndTransactionDateBetweenAndStatus(
                accountId, dateFrom, dateTo, TransactionStatus.RESOLVED);
        long unresolved = transactionRepository.countByAccountIdAndTransactionDateBetweenAndStatus(
                accountId, dateFrom, dateTo, TransactionStatus.UNRESOLVED);
        long matched = settlementReportRepository.countByAccountIdAndTransactionDateBetweenAndReconciliationStatus(
                accountId, dateFrom, dateTo, ReportReconciliationStatus.RECONCILED);
        long mismatched = settlementReportRepository.countByAccountIdAndTransactionDateBetweenAndReconciliationStatus(
                accountId, dateFrom, dateTo, ReportReconciliationStatus.UNRECONCILED);

        BigDecimal totalVariance = discrepancyRepository
                .sumDifferenceByAccountIdAndDateRange(accountId, dateFrom, dateTo);

        return ReconciliationSummaryResponse.builder()
                .accountId(accountId)
                .month(month)
                .totalTransactions(total)
                .resolved(resolved)
                .matched(matched)
                .unresolved(unresolved)
                .mismatched(mismatched)
                .totalVariance(totalVariance)
                .build();
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid month format — expected yyyy-MM, got: " + month);
        }
    }
}
