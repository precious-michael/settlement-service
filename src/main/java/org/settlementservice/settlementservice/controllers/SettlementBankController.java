package org.settlementservice.settlementservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.request.SettlementBankRequest;
import org.settlementservice.settlementservice.dtos.request.SettlementBankUpdateRequest;
import org.settlementservice.settlementservice.dtos.response.SettlementBankResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.SettlementBankService;
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
@RequestMapping("/api/settlement-banks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SettlementBankController {

    private final SettlementBankService settlementBankService;

    @PostMapping
    public ResponseEntity<SettlementServiceResponse<SettlementBankResponse>> create(@Valid @RequestBody SettlementBankRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Settlement bank created successfully", settlementBankService.create(request)));
    }

    @GetMapping
    public SettlementServiceResponse<List<SettlementBankResponse>> getAll() {
        return SettlementServiceResponse.success("Settlement banks retrieved successfully", settlementBankService.getAll());
    }

    @GetMapping("/{id}")
    public SettlementServiceResponse<SettlementBankResponse> getById(@PathVariable Long id) {
        return SettlementServiceResponse.success("Settlement bank retrieved successfully", settlementBankService.getById(id));
    }

    @PutMapping("/{id}")
    public SettlementServiceResponse<SettlementBankResponse> update(@PathVariable Long id, @Valid @RequestBody SettlementBankUpdateRequest request) {
        return SettlementServiceResponse.success("Settlement bank updated successfully", settlementBankService.update(id, request));
    }
}
