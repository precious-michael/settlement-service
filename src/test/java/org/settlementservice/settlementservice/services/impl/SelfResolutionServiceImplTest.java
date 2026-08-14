package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.SelfResolutionRule;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.SelfResolutionRuleRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.SelfResolutionService;
import org.settlementservice.settlementservice.services.SettlementValidationService;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfResolutionServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private SettlementTransactionRepository settlementTransactionRepository;
    @Mock private SettlementValidationService settlementValidationService;
    @Mock private SelfResolutionRuleRepository ruleRepository;
    @Mock private SelfResolutionService self;

    private SelfResolutionServiceImpl service;

    private static final String NIP_PATTERN = "NIP/(?<rrn>[A-Z0-9]{12})/(?<ref>[^/]+)/(?<stan>[0-9]+)";

    @BeforeEach
    void setUp() {
        service = new SelfResolutionServiceImpl(
                transactionRepository, settlementReportRepository,
                settlementTransactionRepository, settlementValidationService, ruleRepository);
        ReflectionTestUtils.setField(service, "self", self);
    }

    // --- resolveOne(Long) ---

    @Test
    void resolveOne_transactionNotFound_throwsResourceNotFoundException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveOne(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveOne_alreadyResolved_returnsFalse() {
        Transaction transaction = transaction(TransactionStatus.RESOLVED, "NIP/AABBCCDD1234/REF001/999001");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        boolean result = service.resolveOne(1L);

        assertThat(result).isFalse();
        verify(settlementReportRepository, never()).save(any());
    }

    @Test
    void resolveOne_noRuleMatches_returnsFalse() {
        Transaction transaction = transaction(TransactionStatus.UNRESOLVED, "UNKNOWN PAYMENT FORMAT");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule("NIP Transfer", NIP_PATTERN)));

        boolean result = service.resolveOne(1L);

        assertThat(result).isFalse();
        verify(settlementReportRepository, never()).save(any());
        verify(settlementValidationService, never()).validateSettlement(any());
    }

    @Test
    void resolveOne_nullNarration_returnsFalse() {
        Transaction transaction = transaction(TransactionStatus.UNRESOLVED, null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        boolean result = service.resolveOne(1L);

        assertThat(result).isFalse();
        verify(ruleRepository, never()).findByActiveTrue();
    }

    @Test
    void resolveOne_ruleMatches_createsSettlementReportAndTransaction() {
        Transaction transaction = transaction(TransactionStatus.UNRESOLVED, "NIP/AABBCCDD1234/REF001/999001");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule("NIP Transfer", NIP_PATTERN)));

        SettlementReport savedReport = new SettlementReport();
        savedReport.setId(10L);
        when(settlementReportRepository.save(any())).thenReturn(savedReport);

        boolean result = service.resolveOne(1L);

        assertThat(result).isTrue();

        ArgumentCaptor<SettlementReport> reportCaptor = ArgumentCaptor.forClass(SettlementReport.class);
        verify(settlementReportRepository).save(reportCaptor.capture());
        SettlementReport report = reportCaptor.getValue();
        assertThat(report.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(report.getTotalEntries()).isEqualTo(1);
        assertThat(report.getFileName()).isEqualTo("auto-resolved");

        ArgumentCaptor<SettlementTransaction> txCaptor = ArgumentCaptor.forClass(SettlementTransaction.class);
        verify(settlementTransactionRepository).save(txCaptor.capture());
        SettlementTransaction settlementTx = txCaptor.getValue();
        assertThat(settlementTx.getRrn()).isEqualTo("AABBCCDD1234");
        assertThat(settlementTx.getTransactionReference()).isEqualTo("REF001");
        assertThat(settlementTx.getStan()).isEqualTo("999001");
        assertThat(settlementTx.getDebit()).isEqualByComparingTo("0");
        assertThat(settlementTx.getCredit()).isEqualByComparingTo("50000");

        verify(settlementValidationService).validateSettlement(10L);
    }

    @Test
    void resolveOne_noRefGroup_fallsBackToTransactionReferenceNumber() {
        Transaction transaction = transaction(TransactionStatus.UNRESOLVED, "TRANSFER VIA BANK");
        transaction.setReferenceNumber("TXN-REF-001");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule("Simple Match", "(?i)TRANSFER.+")));

        SettlementReport savedReport = new SettlementReport();
        savedReport.setId(10L);
        when(settlementReportRepository.save(any())).thenReturn(savedReport);

        service.resolveOne(1L);

        ArgumentCaptor<SettlementTransaction> captor = ArgumentCaptor.forClass(SettlementTransaction.class);
        verify(settlementTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionReference()).isEqualTo("TXN-REF-001");
    }

    // --- resolve(accountId, statementId) ---

    @Test
    void resolve_noUnresolvedTransactions_returnsZero() {
        when(transactionRepository.findByStatus(TransactionStatus.UNRESOLVED)).thenReturn(List.of());

        int count = service.resolve(null, null);

        assertThat(count).isZero();
        verify(self, never()).resolveOne(any());
    }

    @Test
    void resolve_withAccountId_queriesByAccount() {
        when(transactionRepository.findByStatusAndAccountId(TransactionStatus.UNRESOLVED, 5L))
                .thenReturn(List.of());

        service.resolve(5L, null);

        verify(transactionRepository).findByStatusAndAccountId(TransactionStatus.UNRESOLVED, 5L);
        verify(transactionRepository, never()).findByStatus(any());
    }

    @Test
    void resolve_withStatementId_queriesByStatement() {
        when(transactionRepository.findByStatusAndBankStatementId(TransactionStatus.UNRESOLVED, 3L))
                .thenReturn(List.of());

        service.resolve(null, 3L);

        verify(transactionRepository).findByStatusAndBankStatementId(TransactionStatus.UNRESOLVED, 3L);
        verify(transactionRepository, never()).findByStatus(any());
    }

    @Test
    void resolve_delegatesToSelfProxy_countsMismatch() {
        Transaction t1 = transaction(TransactionStatus.UNRESOLVED, "NIP/AABBCCDD1234/REF001/001");
        t1.setId(1L);
        Transaction t2 = transaction(TransactionStatus.UNRESOLVED, "UNKNOWN FORMAT");
        t2.setId(2L);
        when(transactionRepository.findByStatus(TransactionStatus.UNRESOLVED)).thenReturn(List.of(t1, t2));
        when(self.resolveOne(1L)).thenReturn(true);
        when(self.resolveOne(2L)).thenReturn(false);

        int count = service.resolve(null, null);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void resolve_oneTransactionThrows_continuesAndCounts() {
        Transaction t1 = transaction(TransactionStatus.UNRESOLVED, "NIP/AABBCCDD1234/REF001/001");
        t1.setId(1L);
        Transaction t2 = transaction(TransactionStatus.UNRESOLVED, "NIP/XXYYZZ556677/REF002/002");
        t2.setId(2L);
        when(transactionRepository.findByStatus(TransactionStatus.UNRESOLVED)).thenReturn(List.of(t1, t2));
        when(self.resolveOne(1L)).thenThrow(new RuntimeException("DB timeout"));
        when(self.resolveOne(2L)).thenReturn(true);

        int count = service.resolve(null, null);

        assertThat(count).isEqualTo(1);
    }

    // --- helpers ---

    private Transaction transaction(TransactionStatus status, String narration) {
        Transaction t = new Transaction();
        t.setId(1L);
        t.setStatus(status);
        t.setNarration(narration);
        t.setTransactionDate(LocalDate.of(2026, 7, 1));
        t.setDebit(BigDecimal.ZERO);
        t.setCredit(new BigDecimal("50000"));
        t.setAccount(new Account());
        return t;
    }

    private SelfResolutionRule rule(String name, String pattern) {
        SelfResolutionRule rule = new SelfResolutionRule();
        rule.setName(name);
        rule.setPattern(pattern);
        rule.setActive(true);
        return rule;
    }
}
