package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.SettlementReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settlement-reports")
@RequiredArgsConstructor
public class SettlementReportController {

    private final SettlementReportService settlementReportService;

    @PostMapping("/upload")
    public ResponseEntity<SettlementServiceResponse<SettlementReportUploadResponse>> upload(
            @RequestParam("transactionId") Long transactionId,
            @RequestParam("file") MultipartFile file) {
        SettlementReportUploadResponse response = settlementReportService.upload(transactionId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Settlement report uploaded successfully", response));
    }
}
