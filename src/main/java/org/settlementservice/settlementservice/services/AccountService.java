package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.dtos.response.AccountResponse;
import org.settlementservice.settlementservice.dtos.request.AccountUpdateRequest;

import java.util.List;

public interface AccountService {

    AccountResponse create(AccountRequest request);

    AccountResponse getById(Long id);

    List<AccountResponse> getAll();

    AccountResponse update(Long id, AccountUpdateRequest request);

    void delete(Long id);
}
