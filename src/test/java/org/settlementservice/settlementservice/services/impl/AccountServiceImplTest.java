package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.dtos.request.AccountUpdateRequest;
import org.settlementservice.settlementservice.dtos.response.AccountResponse;
import org.settlementservice.settlementservice.enums.AccountStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SettlementBankRepository settlementBankRepository;

    @Mock
    private BankStatementRepository bankStatementRepository;

    private AccountServiceImpl accountService;

    private SettlementBank bank;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository, settlementBankRepository,
                new ModelMapper());

        bank = new SettlementBank();
        bank.setId(2L);
        bank.setName("Guaranty Trust Bank");
        bank.setCode("058");
    }

    private AccountRequest accountRequest() {
        AccountRequest request = new AccountRequest();
        request.setName("Test Corp");
        request.setAccountNumber("1234567890");
        request.setBankId(2L);
        return request;
    }

    @Test
    void create_newAccountNumber_savesAsActiveAndReturnsBankIdAndName() {
        when(accountRepository.findByAccountNumber("1234567890")).thenReturn(Optional.empty());
        when(settlementBankRepository.findById(2L)).thenReturn(Optional.of(bank));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        AccountResponse response = accountService.create(accountRequest());

        assertThat(response.getId()).isEqualTo(6L);
        assertThat(response.getName()).isEqualTo("Test Corp");
        assertThat(response.getAccountNumber()).isEqualTo("1234567890");
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getBankId()).isEqualTo(2L);
        assertThat(response.getBankName()).isEqualTo("Guaranty Trust Bank");
    }

    @Test
    void create_duplicateAccountNumber_throwsDuplicateResourceException() {
        when(accountRepository.findByAccountNumber("1234567890")).thenReturn(Optional.of(new Account()));

        assertThatThrownBy(() -> accountService.create(accountRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("1234567890");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void create_settlementBankNotFound_throwsResourceNotFoundException() {
        when(accountRepository.findByAccountNumber("1234567890")).thenReturn(Optional.empty());
        when(settlementBankRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.create(accountRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("2");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void getById_existingAccount_returnsResponse() {
        Account account = accountWithBank(1L, "Test Corp", "1234567890", AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getBankName()).isEqualTo("Guaranty Trust Bank");
    }

    @Test
    void getById_missingAccount_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returnsAllAccountsMapped() {
        Account first = accountWithBank(1L, "Corp One", "1111111111", AccountStatus.ACTIVE);
        Account second = accountWithBank(2L, "Corp Two", "2222222222", AccountStatus.INACTIVE);
        when(accountRepository.findAll()).thenReturn(List.of(first, second));

        List<AccountResponse> responses = accountService.getAll();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(AccountResponse::getName).containsExactly("Corp One", "Corp Two");
    }

    @Test
    void update_existingAccount_changesOnlyStatus() {
        Account account = accountWithBank(1L, "Test Corp", "1234567890", AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setStatus(AccountStatus.INACTIVE);

        AccountResponse response = accountService.update(1L, request);

        assertThat(response.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(response.getName()).isEqualTo("Test Corp");
        assertThat(response.getAccountNumber()).isEqualTo("1234567890");
    }

    @Test
    void update_missingAccount_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setStatus(AccountStatus.INACTIVE);

        assertThatThrownBy(() -> accountService.update(99L, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_existingAccount_deletesIt() {
        Account account = accountWithBank(1L, "Test Corp", "1234567890", AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        accountService.delete(1L);

        verify(accountRepository).delete(account);
    }

    @Test
    void delete_missingAccount_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.delete(99L)).isInstanceOf(ResourceNotFoundException.class);

        verify(accountRepository, never()).delete(any());
    }

    private Account accountWithBank(Long id, String name, String accountNumber, AccountStatus status) {
        Account account = new Account();
        account.setId(id);
        account.setName(name);
        account.setAccountNumber(accountNumber);
        account.setStatus(status);
        account.setSettlementBank(bank);
        return account;
    }
}
