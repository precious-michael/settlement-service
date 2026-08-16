package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.dtos.response.AccountResponse;
import org.settlementservice.settlementservice.dtos.request.AccountUpdateRequest;
import org.settlementservice.settlementservice.enums.AccountStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.settlementservice.settlementservice.services.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final SettlementBankRepository settlementBankRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public AccountResponse create(AccountRequest request) {
        if (accountRepository.findByAccountNumber(request.getAccountNumber()).isPresent()) {
            throw new DuplicateResourceException(
                    "An account with account number " + request.getAccountNumber() + " already exists");
        }

        SettlementBank bank = settlementBankRepository.findById(request.getBankId())
                .orElseThrow(() -> new ResourceNotFoundException("No settlement bank found with id " + request.getBankId()));


        Account account = new Account();
        account.setName(request.getName());
        account.setAccountNumber(request.getAccountNumber());
        account.setSettlementBank(bank);
        account.setStatus(AccountStatus.ACTIVE);
        account.setOpeningBalance(request.getOpeningBalance() != null ? request.getOpeningBalance() : BigDecimal.ZERO);
        account.setDescription(request.getDescription());

        return toResponse(accountRepository.save(account));
    }

    @Override
    public AccountResponse getById(Long id) {
        return toResponse(findAccountOrThrow(id));
    }

    @Override
    public List<AccountResponse> getAll() {
        return accountRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request) {
        Account account = findAccountOrThrow(id);
        account.setStatus(request.getStatus());
        if (request.getOpeningBalance() != null) {
            account.setOpeningBalance(request.getOpeningBalance());
        }
        if (request.getDescription() != null) {
            account.setDescription(request.getDescription());
        }
        return toResponse(account);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Account account = findAccountOrThrow(id);
        accountRepository.delete(account);
    }

    private Account findAccountOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with id " + id));
    }

    private AccountResponse toResponse(Account account) {
        AccountResponse response = modelMapper.map(account, AccountResponse.class);
        response.setBankId(account.getSettlementBank().getId());
        response.setBankName(account.getSettlementBank().getName());
        return response;
    }
}
