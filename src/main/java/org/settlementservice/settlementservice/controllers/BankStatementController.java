package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.BankStatementUploadResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.dtos.response.TransactionResponse;
import org.settlementservice.settlementservice.services.BankStatementService;
import org.settlementservice.settlementservice.services.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/bank-statements")
@RequiredArgsConstructor
public class BankStatementController {

    private final BankStatementService bankStatementService;
    private final TransactionService transactionService;

    @PostMapping("/upload")
    public ResponseEntity<SettlementServiceResponse<BankStatementUploadResponse>> upload(
            @RequestParam("accountId") Long accountId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("openingBalance") BigDecimal openingBalance) {
        BankStatementUploadResponse response = bankStatementService.upload(accountId, file, openingBalance);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Bank statement uploaded successfully", response));
    }

    /**
     * Get the current status of an uploaded bank statement.
     * Useful for tracking async file processing progress.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SettlementServiceResponse<BankStatementUploadResponse>> getStatus(
            @PathVariable Long id) {
        BankStatementUploadResponse response = bankStatementService.getStatus(id);
        return ResponseEntity.ok(SettlementServiceResponse.success("Bank statement status retrieved", response));
    }

    /**
     * Get all transactions for a bank statement (paginated).
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<SettlementServiceResponse<Page<TransactionResponse>>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransactionResponse> transactions = transactionService.getByBankStatementId(id, PageRequest.of(page, size));
        return ResponseEntity.ok(SettlementServiceResponse.success("Transactions retrieved successfully", transactions));
    }
}
