package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementReportServiceImplTest {

    @Mock
    private SettlementReportRepository settlementReportRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private SettlementReportServiceImpl settlementReportService;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        settlementReportService = new SettlementReportServiceImpl(
                settlementReportRepository, transactionRepository, new ModelMapper());

        Account account = new Account();
        account.setId(3L);
        transaction = new Transaction();
        transaction.setId(1L);
        transaction.setAccount(account);
    }

    @Test
    void upload_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "report.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> settlementReportService.upload(1L, emptyFile))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_transactionNotFound_throwsResourceNotFoundException() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> settlementReportService.upload(1L, file))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upload_transactionAlreadyHasReport_throwsDuplicateResourceException() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(settlementReportRepository.existsByTransactionId(1L)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> settlementReportService.upload(1L, file))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("1");

        verify(settlementReportRepository, never()).save(any());
    }

    @Test
    void upload_newReport_savesPendingBatchLinkedToTransactionAndItsAccount() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(settlementReportRepository.existsByTransactionId(1L)).thenReturn(false);
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(invocation -> {
            SettlementReport saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv", "data".getBytes());
        SettlementReportUploadResponse response = settlementReportService.upload(1L, file);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFileName()).isEqualTo("report.csv");
        assertThat(response.getStatus()).isEqualTo(BatchStatus.PENDING);
    }
}
