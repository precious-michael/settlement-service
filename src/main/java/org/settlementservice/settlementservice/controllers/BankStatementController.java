package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.BankStatementUploadResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.BankStatementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/bank-statements")
@RequiredArgsConstructor
public class BankStatementController {

    private final BankStatementService bankStatementService;

    @PostMapping("/upload")
    public ResponseEntity<SettlementServiceResponse<BankStatementUploadResponse>> upload(
            @RequestParam("accountId") Long accountId,
            @RequestParam("file") MultipartFile file) {
        BankStatementUploadResponse response = bankStatementService.upload(accountId, file);
        String message = response.isDuplicate()
                ? "This file has already been uploaded for this account"
                : "Bank statement uploaded successfully";
        return ResponseEntity.status(HttpStatus.CREATED).body(SettlementServiceResponse.success(message, response));
    }
}
