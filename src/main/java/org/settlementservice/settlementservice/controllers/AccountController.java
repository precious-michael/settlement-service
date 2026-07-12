package org.settlementservice.settlementservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.dtos.response.AccountResponse;
import org.settlementservice.settlementservice.dtos.request.AccountUpdateRequest;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<SettlementServiceResponse<AccountResponse>> create(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Account created successfully", accountService.create(request)));
    }

    @GetMapping
    public SettlementServiceResponse<List<AccountResponse>> getAll() {
        return SettlementServiceResponse.success("Accounts retrieved successfully", accountService.getAll());
    }

    @GetMapping("/{id}")
    public SettlementServiceResponse<AccountResponse> getById(@PathVariable Long id) {
        return SettlementServiceResponse.success("Account retrieved successfully", accountService.getById(id));
    }

    @PutMapping("/{id}")
    public SettlementServiceResponse<AccountResponse> update(@PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request) {
        return SettlementServiceResponse.success("Account updated successfully", accountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public SettlementServiceResponse<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return SettlementServiceResponse.success("Account deleted successfully");
    }
}
