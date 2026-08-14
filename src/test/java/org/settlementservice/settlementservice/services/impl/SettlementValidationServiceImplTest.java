package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationSummaryResponse;
import org.settlementservice.settlementservice.enums.ReportReconciliationStatus;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementValidationServiceImplTest {

    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private DiscrepancyRepository discrepancyRepository;

    private SettlementValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SettlementValidationServiceImpl(
                settlementReportRepository, transactionRepository, discrepancyRepository);
    }

    // --- validateSettlement() ---

    @Test
    void validateSettlement_reportNotFound_throwsResourceNotFoundException() {
        when(settlementReportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateSettlement(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void validateSettlement_reportFound_marksTransactionResolvedAndSaves() {
        Transaction transaction = new Transaction();
        transaction.setId(10L);
        transaction.setStatus(TransactionStatus.UNRESOLVED);
        SettlementReport report = new SettlementReport();
        report.setId(1L);
        report.setTransaction(transaction);
        when(settlementReportRepository.findById(1L)).thenReturn(Optional.of(report));

        service.validateSettlement(1L);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.RESOLVED);
        verify(transactionRepository).save(transaction);
    }

    // --- getSummary() ---

    @Test
    void getSummary_validMonth_returnsAggregatedCounts() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to   = LocalDate.of(2026, 6, 30);

        when(transactionRepository.countByAccountIdAndTransactionDateBetween(5L, from, to)).thenReturn(10L);
        when(transactionRepository.countByAccountIdAndTransactionDateBetweenAndStatus(
                5L, from, to, TransactionStatus.RESOLVED)).thenReturn(8L);
        when(transactionRepository.countByAccountIdAndTransactionDateBetweenAndStatus(
                5L, from, to, TransactionStatus.UNRESOLVED)).thenReturn(2L);
        when(settlementReportRepository.countByAccountIdAndTransactionDateBetweenAndReconciliationStatus(
                5L, from, to, ReportReconciliationStatus.RECONCILED)).thenReturn(7L);
        when(settlementReportRepository.countByAccountIdAndTransactionDateBetweenAndReconciliationStatus(
                5L, from, to, ReportReconciliationStatus.UNRECONCILED)).thenReturn(1L);
        when(discrepancyRepository.sumDifferenceByAccountIdAndDateRange(5L, from, to))
                .thenReturn(new BigDecimal("2000.0000"));

        ReconciliationSummaryResponse summary = service.getSummary(5L, "2026-06");

        assertThat(summary.getAccountId()).isEqualTo(5L);
        assertThat(summary.getMonth()).isEqualTo("2026-06");
        assertThat(summary.getTotalTransactions()).isEqualTo(10L);
        assertThat(summary.getResolved()).isEqualTo(8L);
        assertThat(summary.getUnresolved()).isEqualTo(2L);
        assertThat(summary.getMatched()).isEqualTo(7L);
        assertThat(summary.getMismatched()).isEqualTo(1L);
        assertThat(summary.getTotalVariance()).isEqualByComparingTo("2000");
    }

    @Test
    void getSummary_invalidMonthFormat_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getSummary(1L, "June-2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM");
    }
}
