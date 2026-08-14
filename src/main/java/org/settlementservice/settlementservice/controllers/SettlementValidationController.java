package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationSummaryResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.SettlementValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlement-validation")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
@RequiredArgsConstructor
public class SettlementValidationController {

    private final SettlementValidationService settlementValidationService;

    @GetMapping("/summary")
    public ResponseEntity<SettlementServiceResponse<ReconciliationSummaryResponse>> getSummary(
            @RequestParam Long accountId,
            @RequestParam String month) {
        ReconciliationSummaryResponse summary = settlementValidationService.getSummary(accountId, month);
        return ResponseEntity.ok(SettlementServiceResponse.success("Summary retrieved successfully", summary));
    }
}
