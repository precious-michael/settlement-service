package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.enums.AsyncTaskStatus;
import org.settlementservice.settlementservice.enums.AsyncTaskType;
import org.settlementservice.settlementservice.models.AsyncTask;
import org.settlementservice.settlementservice.repositories.AsyncTaskRepository;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.services.SelfResolutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
@RequiredArgsConstructor
public class SelfResolutionController {

    private final SelfResolutionService selfResolutionService;
    private final ReconciliationFormulaRepository reconciliationFormulaRepository;
    private final AsyncTaskRepository asyncTaskRepository;

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

        // Validate that account has a default reconciliation formula if accountId is specified
        if (accountId != null) {
            boolean hasDefaultFormula = reconciliationFormulaRepository
                    .findByAccountIdAndIsDefaultTrue(accountId)
                    .isPresent();

            if (!hasDefaultFormula) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(SettlementServiceResponse.<String>builder()
                                .success(false)
                                .error("Account must have a default reconciliation formula before self-resolution can be performed. " +
                                        "Please create a reconciliation formula and mark it as default.")
                                .build());
            }
        }

        // Create AsyncTask to track progress
        AsyncTask task = asyncTaskRepository.save(AsyncTask.builder()
                .type(AsyncTaskType.SELF_RESOLUTION)
                .status(AsyncTaskStatus.PROCESSING)
                .totalRecords(0L) // Will be updated when async method gets count
                .processedRecords(0L)
                .startedAt(Instant.now())
                .build());

        // Submit async self-resolution
        selfResolutionService.resolveAsync(accountId, statementId)
                .whenComplete((count, ex) -> {
                    if (ex != null) {
                        task.setStatus(AsyncTaskStatus.FAILED);
                        task.setErrorMessage(ex.getMessage());
                    } else {
                        task.setStatus(AsyncTaskStatus.COMPLETED);
                        task.setProcessedRecords((long) count);
                        task.setTotalRecords((long) count);
                    }
                    task.setCompletedAt(Instant.now());
                    asyncTaskRepository.save(task);
                });

        return ResponseEntity.ok(SettlementServiceResponse.success("Self-resolution started", Map.of("taskId", task.getId())));
    }
}
