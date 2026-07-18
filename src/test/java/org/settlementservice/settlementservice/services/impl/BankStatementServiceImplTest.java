package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.async.BankStatementUploadTask;
import org.settlementservice.settlementservice.dtos.response.BankStatementUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankStatementServiceImplTest {

    @Mock
    private BankStatementRepository bankStatementRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BankStatementUploadTask bankStatementUploadTask;

    @Mock
    private TransactionTemplate transactionTemplate;

    private BankStatementServiceImpl bankStatementService;

    private Account account;

    @BeforeEach
    void setUp() {
        bankStatementService = new BankStatementServiceImpl(
                bankStatementRepository, accountRepository, new ModelMapper(), bankStatementUploadTask, transactionTemplate);
        account = new Account();
        account.setId(3L);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void upload_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "statement.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> bankStatementService.upload(3L, emptyFile, new BigDecimal("100000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void upload_accountNotFound_throwsResourceNotFoundException() {
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "some data".getBytes());

        assertThatThrownBy(() -> bankStatementService.upload(3L, file, new BigDecimal("100000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upload_newFile_createsPendingBatchAndTriggersProcessing() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(account));
        when(bankStatementRepository.findByAccountIdAndFileHash(eq(3L), anyString())).thenReturn(Optional.empty());
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(invocation -> {
            BankStatement saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "some data".getBytes());
        BankStatementUploadResponse response = bankStatementService.upload(3L, file, new BigDecimal("100000"));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFileName()).isEqualTo("statement.csv");
        assertThat(response.getStatus()).isEqualTo(BatchStatus.PENDING);
        verify(bankStatementUploadTask).process(eq(1L), eq("statement.csv"), any());
    }

    @Test
    void upload_sameFileAlreadyUploadedForAccount_throwsDuplicateResourceExceptionWithoutSavingOrProcessing() {
        when(accountRepository.findById(3L)).thenReturn(Optional.of(account));
        BankStatement existing = new BankStatement();
        existing.setId(1L);
        existing.setFileName("statement.csv");
        existing.setStatus(BatchStatus.PENDING);
        when(bankStatementRepository.findByAccountIdAndFileHash(eq(3L), anyString())).thenReturn(Optional.of(existing));

        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "some data".getBytes());

        assertThatThrownBy(() -> bankStatementService.upload(3L, file, new BigDecimal("100000")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(bankStatementRepository, never()).save(any());
        verify(bankStatementUploadTask, never()).process(any(), any(), any());
    }
}
