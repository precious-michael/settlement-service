package org.settlementservice.settlementservice.reconciliation.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.DiscrepancyResponse;
import org.settlementservice.settlementservice.reconciliation.dtos.ReconciliationRunResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.DiscrepancyService;
import org.settlementservice.settlementservice.reconciliation.services.ReconciliationEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationEngine reconciliationEngine;
    private final DiscrepancyService discrepancyService;

    @PostMapping("/reconciliation/run")
    public ResponseEntity<SettlementServiceResponse<ReconciliationRunResponse>> run() {
        ReconciliationRunResponse result = reconciliationEngine.run();
        return ResponseEntity.ok(SettlementServiceResponse.success("Reconciliation run complete", result));
    }

    @GetMapping("/reconciliation/results")
    public ResponseEntity<SettlementServiceResponse<Page<DiscrepancyResponse>>> results(
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        Page<DiscrepancyResponse> results = discrepancyService.search(transactionId, type, pageable);
        return ResponseEntity.ok(SettlementServiceResponse.success("Results retrieved", results));
    }
}
