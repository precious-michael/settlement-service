package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.dtos.response.TransactionResponse;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.services.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<SettlementServiceResponse<Page<TransactionResponse>>> search(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransactionResponse> results =
                transactionService.search(status, accountId, productType, dateFrom, dateTo, PageRequest.of(page, size));
        return ResponseEntity.ok(SettlementServiceResponse.success("Transactions retrieved successfully", results));
    }
}
