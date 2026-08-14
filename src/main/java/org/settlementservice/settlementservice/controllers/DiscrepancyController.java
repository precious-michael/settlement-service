package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.DiscrepancyResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.DiscrepancyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discrepancies")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
@RequiredArgsConstructor
public class DiscrepancyController {

    private final DiscrepancyService discrepancyService;

    @GetMapping
    public ResponseEntity<SettlementServiceResponse<Page<DiscrepancyResponse>>> search(
            @RequestParam(required = false) Long transactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DiscrepancyResponse> results =
                discrepancyService.search(transactionId, PageRequest.of(page, size));
        return ResponseEntity.ok(SettlementServiceResponse.success("Discrepancies retrieved successfully", results));
    }
}
