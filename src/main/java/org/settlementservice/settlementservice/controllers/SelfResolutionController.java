package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.SelfResolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
@RequiredArgsConstructor
public class SelfResolutionController {

    private final SelfResolutionService selfResolutionService;

    /**
     * Trigger self-resolution. Exactly one scope applies:
     * - transactionId → resolves that single transaction
     * - accountId     → resolves all UNRESOLVED transactions for that account
     * - statementId   → resolves all UNRESOLVED transactions in that bank statement
     * - (none)        → resolves all UNRESOLVED transactions across all accounts
     */
    @PostMapping("/self-resolve")
    public ResponseEntity<SettlementServiceResponse<?>> selfResolve(
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long statementId) {

        if (transactionId != null) {
            boolean resolved = selfResolutionService.resolveOne(transactionId);
            String message = resolved ? "Transaction resolved" : "Transaction skipped — already resolved or no rule matched";
            return ResponseEntity.ok(SettlementServiceResponse.success(message, Map.of("resolved", resolved)));
        }

        int count = selfResolutionService.resolve(accountId, statementId);
        return ResponseEntity.ok(SettlementServiceResponse.success("Self-resolution complete", Map.of("resolved", count)));
    }
}
