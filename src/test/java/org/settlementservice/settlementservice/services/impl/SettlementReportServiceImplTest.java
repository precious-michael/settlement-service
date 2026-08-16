package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.async.SettlementReportUploadTask;
import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementReportServiceImplTest {

    @Mock
    private SettlementReportRepository settlementReportRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SettlementTransactionRepository settlementTransactionRepository;

    @Mock
    private DiscrepancyRepository discrepancyRepository;

    @Mock
    private ReconciliationFormulaRepository reconciliationFormulaRepository;

    @Mock
    private SettlementReportUploadTask settlementReportUploadTask;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager entityManager;

    private SettlementReportServiceImpl settlementReportService;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        settlementReportService = new SettlementReportServiceImpl(
                settlementReportRepository, transactionRepository, settlementTransactionRepository,
                discrepancyRepository, reconciliationFormulaRepository, new ModelMapper(),
                settlementReportUploadTask, transactionTemplate, entityManager);

        Account account = new Account();
        account.setId(3L);
        transaction = new Transaction();
        transaction.setId(1L);
        transaction.setAccount(account);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void upload_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "report.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> settlementReportService.upload(1L, emptyFile, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_transactionNotFound_throwsResourceNotFoundException() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> settlementReportService.upload(1L, file, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upload_transactionAlreadyHasNonFailedReport_throwsDuplicateResourceExceptionWithoutSavingOrProcessing() {
        SettlementReport existingReport = new SettlementReport();
        existingReport.setId(5L);
        existingReport.setStatus(BatchStatus.COMPLETED); // Non-failed status

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(settlementReportRepository.findByTransactionId(1L)).thenReturn(Optional.of(existingReport));
        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> settlementReportService.upload(1L, file, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("COMPLETED");

        verify(settlementReportRepository, never()).save(any());
        verify(settlementReportUploadTask, never()).process(any(), any(), any());
    }

    @Test
    void upload_transactionHasFailedReport_deletesOldReportAndCreatesNew() {
        SettlementReport existingFailedReport = new SettlementReport();
        existingFailedReport.setId(5L);
        existingFailedReport.setStatus(BatchStatus.FAILED);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(settlementReportRepository.findByTransactionId(1L)).thenReturn(Optional.of(existingFailedReport));
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(invocation -> {
            SettlementReport saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());
        SettlementReportUploadResponse response = settlementReportService.upload(1L, file, null);

        // Verify old report and its data were deleted
        verify(discrepancyRepository).deleteBySettlementReportId(5L);
        verify(settlementTransactionRepository).deleteBySettlementReportId(5L);
        verify(settlementReportRepository).delete(existingFailedReport);

        // Verify new report was created and processed
        assertThat(response.getId()).isEqualTo(10L);
        verify(settlementReportUploadTask).process(eq(10L), eq("report.csv"), any());
    }

    @Test
    void upload_newReport_savesPendingBatchLinkedToTransactionAndTriggersProcessing() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(settlementReportRepository.findByTransactionId(1L)).thenReturn(Optional.empty());
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(invocation -> {
            SettlementReport saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());
        SettlementReportUploadResponse response = settlementReportService.upload(1L, file, null);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFileName()).isEqualTo("report.csv");
        assertThat(response.getStatus()).isEqualTo(BatchStatus.PENDING);
        verify(settlementReportUploadTask).process(eq(1L), eq("report.csv"), any());
    }
}
