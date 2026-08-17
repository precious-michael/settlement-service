package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.Discrepancy;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationRunResponse;
import org.settlementservice.settlementservice.enums.ReconciliationStatus;
import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.models.ReconciliationFormula;
import org.settlementservice.settlementservice.repositories.AsyncTaskRepository;
import org.settlementservice.settlementservice.repositories.InternalRecordRepository;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.reconciliation.services.impl.ReconciliationEngineImpl;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationEngineImplTest {

    @Mock private SettlementTransactionRepository settlementTransactionRepository;
    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private InternalRecordRepository internalRecordRepository;
    @Mock private DiscrepancyRepository discrepancyRepository;
    @Mock private ReconciliationFormulaRepository reconciliationFormulaRepository;
    @Mock private AsyncTaskRepository asyncTaskRepository;

    private ReconciliationEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new ReconciliationEngineImpl(
                settlementTransactionRepository,
                settlementReportRepository,
                internalRecordRepository,
                discrepancyRepository,
                reconciliationFormulaRepository,
                asyncTaskRepository);
    }

    @Test
    void run_noPendingTransactions_returnsAllZero() {
        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of());

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getTotalProcessed()).isZero();
        assertThat(result.getMatched()).isZero();
        assertThat(result.getMismatched()).isZero();
        assertThat(result.getNoMatchFound()).isZero();
    }

    @Test
    void run_noFormula_marksMissing() {
        SettlementTransaction tx = transaction("REF-001", null, null, BigDecimal.ZERO, new BigDecimal("1000"), null);
        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(tx));
        when(reconciliationFormulaRepository.findByAccountIdAndIsDefaultTrue(1L))
                .thenReturn(Optional.empty());

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getNoMatchFound()).isEqualTo(1);
        assertThat(result.getMatched()).isZero();
        assertThat(tx.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISSING);
        verify(internalRecordRepository, never()).findFirstByReferenceNumber(any());
    }

    @Test
    void run_noInternalRecord_marksMissing() {
        ReconciliationFormula formula = formula("${referenceNumber}");
        SettlementTransaction tx = transaction("REF-001", null, null, BigDecimal.ZERO, new BigDecimal("1000"), formula);
        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(tx));
        when(internalRecordRepository.findFirstByReferenceNumber("REF-001"))
                .thenReturn(Optional.empty());

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getNoMatchFound()).isEqualTo(1);
        assertThat(result.getMatched()).isZero();
        assertThat(tx.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISSING);
        verify(settlementTransactionRepository).saveAll(List.of(tx));
    }

    @Test
    void run_amountsMatch_marksReconciled() {
        ReconciliationFormula formula = formula("${referenceNumber}");
        SettlementTransaction tx = transaction("REF-001", null, null, BigDecimal.ZERO, new BigDecimal("5000"), formula);
        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(tx));
        when(internalRecordRepository.findFirstByReferenceNumber("REF-001"))
                .thenReturn(Optional.of(internalRecord("REF-001", BigDecimal.ZERO, new BigDecimal("5000"))));

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(result.getMismatched()).isZero();
        assertThat(tx.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
        verify(discrepancyRepository, never()).save(any());
    }

    @Test
    void run_amountsMismatch_marksUnreconciledAndCreatesDiscrepancy() {
        ReconciliationFormula formula = formula("${referenceNumber}");
        SettlementTransaction tx = transaction("REF-001", null, null, BigDecimal.ZERO, new BigDecimal("5000"), formula);
        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(tx));
        when(internalRecordRepository.findFirstByReferenceNumber("REF-001"))
                .thenReturn(Optional.of(internalRecord("REF-001", BigDecimal.ZERO, new BigDecimal("4500"))));

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getMismatched()).isEqualTo(1);
        assertThat(result.getMatched()).isZero();
        assertThat(tx.getReconciliationStatus()).isEqualTo(ReconciliationStatus.UNRECONCILED);

        ArgumentCaptor<List<Discrepancy>> captor = ArgumentCaptor.forClass(List.class);
        verify(discrepancyRepository).saveAll(captor.capture());
        Discrepancy discrepancy = captor.getValue().get(0);
        assertThat(discrepancy.getExpectedAmount()).isEqualByComparingTo("4500");
        assertThat(discrepancy.getReportedAmount()).isEqualByComparingTo("5000");
        assertThat(discrepancy.getDifference()).isEqualByComparingTo("500");  // Absolute difference
    }

    @Test
    void run_rrnFormula_usesRrnForMatching() {
        ReconciliationFormula formula = formula("${rrn}");
        SettlementTransaction tx = transaction("REF-001", "RRN-123", null, BigDecimal.ZERO, new BigDecimal("1000"), formula);

        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(tx));
        when(internalRecordRepository.findFirstByRrn(eq("RRN-123")))
                .thenReturn(Optional.of(internalRecord("REF-001", "RRN-123", BigDecimal.ZERO, new BigDecimal("1000"))));

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(tx.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    @Test
    void run_rrnAndStanFormula_matchesOnBoth() {
        ReconciliationFormula formula = formula("${rrn}/${stan}");
        SettlementTransaction tx = transaction("REF-001", "RRN-123", "STAN-456", BigDecimal.ZERO, new BigDecimal("1000"), formula);

        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(tx));
        when(internalRecordRepository.findFirstByRrnAndStan(eq("RRN-123"), eq("STAN-456")))
                .thenReturn(Optional.of(internalRecord("REF-001", "RRN-123", "STAN-456", BigDecimal.ZERO, new BigDecimal("1000"))));

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(tx.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    }

    @Test
    void run_mixedBatch_correctCounts() {
        ReconciliationFormula formula = formula("${referenceNumber}");
        SettlementTransaction t1 = transaction("REF-001", null, null, BigDecimal.ZERO, new BigDecimal("1000"), formula);
        SettlementTransaction t2 = transaction("REF-002", null, null, BigDecimal.ZERO, new BigDecimal("2000"), formula);
        SettlementTransaction t3 = transaction("REF-003", null, null, BigDecimal.ZERO, new BigDecimal("3000"), formula);

        when(settlementTransactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING))
                .thenReturn(List.of(t1, t2, t3));
        when(internalRecordRepository.findFirstByReferenceNumber("REF-001"))
                .thenReturn(Optional.of(internalRecord("REF-001", BigDecimal.ZERO, new BigDecimal("1000"))));
        when(internalRecordRepository.findFirstByReferenceNumber("REF-002"))
                .thenReturn(Optional.of(internalRecord("REF-002", BigDecimal.ZERO, new BigDecimal("1800"))));
        when(internalRecordRepository.findFirstByReferenceNumber("REF-003"))
                .thenReturn(Optional.empty());

        ReconciliationRunResponse result = engine.run();

        assertThat(result.getTotalProcessed()).isEqualTo(3);
        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(result.getMismatched()).isEqualTo(1);
        assertThat(result.getNoMatchFound()).isEqualTo(1);
    }

    // --- helpers ---

    private static long txIdSeq = 1;

    private Account account() {
        Account account = new Account();
        account.setId(1L);
        account.setName("Test Account");
        return account;
    }

    private ReconciliationFormula formula(String formulaExpression) {
        ReconciliationFormula formula = new ReconciliationFormula();
        formula.setId(1L);
        formula.setAccount(account());
        formula.setName("Test Formula");
        formula.setFormula(formulaExpression);
        formula.setDefault(true);
        formula.setActive(true);
        return formula;
    }

    private SettlementTransaction transaction(String ref, String rrn, String stan,
                                               BigDecimal debit, BigDecimal credit, ReconciliationFormula formula) {
        Account account = account();

        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setAccount(account);
        tx.setTransactionDate(LocalDate.of(2026, 7, 1));

        SettlementReport report = new SettlementReport();
        report.setId(1L);
        report.setTransaction(tx);
        report.setAccount(account);
        report.setReconciliationFormula(formula);

        SettlementTransaction line = new SettlementTransaction();
        line.setId(txIdSeq++);
        line.setSettlementReport(report);
        line.setTransactionDate(LocalDate.of(2026, 7, 1));
        line.setNarration("test");
        line.setTransactionReference(ref);
        line.setRrn(rrn);
        line.setStan(stan);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setReconciliationStatus(ReconciliationStatus.PENDING);
        return line;
    }

    private InternalRecord internalRecord(String referenceNumber, BigDecimal debit, BigDecimal credit) {
        InternalRecord r = new InternalRecord();
        r.setReferenceNumber(referenceNumber);
        r.setDebit(debit);
        r.setCredit(credit);
        return r;
    }

    private InternalRecord internalRecord(String referenceNumber, String rrn, BigDecimal debit, BigDecimal credit) {
        InternalRecord r = internalRecord(referenceNumber, debit, credit);
        r.setRrn(rrn);
        return r;
    }

    private InternalRecord internalRecord(String referenceNumber, String rrn, String stan, BigDecimal debit, BigDecimal credit) {
        InternalRecord r = internalRecord(referenceNumber, rrn, debit, credit);
        r.setStan(stan);
        return r;
    }
}
