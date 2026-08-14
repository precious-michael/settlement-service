package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.dtos.request.CreateReconciliationFormulaRequest;
import org.settlementservice.settlementservice.dtos.response.ReconciliationFormulaResponse;
import org.settlementservice.settlementservice.dtos.request.UpdateReconciliationFormulaRequest;
import org.settlementservice.settlementservice.services.ReconciliationFormulaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for managing reconciliation formulas.
 */
@RestController
@RequestMapping("/api/reconciliation-formulas")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationFormulaController {

    private final ReconciliationFormulaService reconciliationFormulaService;

    /**
     * Get all reconciliation formulas for an account.
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<ReconciliationFormulaResponse>> getFormulasByAccount(@PathVariable Long accountId) {
        log.info("Getting reconciliation formulas for account: {}", accountId);
        List<ReconciliationFormulaResponse> formulas = reconciliationFormulaService.getFormulasByAccount(accountId);
        return ResponseEntity.ok(formulas);
    }

    /**
     * Get a specific reconciliation formula by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReconciliationFormulaResponse> getFormulaById(@PathVariable Long id) {
        log.info("Getting reconciliation formula: {}", id);
        ReconciliationFormulaResponse formula = reconciliationFormulaService.getFormulaById(id);
        return ResponseEntity.ok(formula);
    }

    /**
     * Create a new reconciliation formula.
     */
    @PostMapping
    public ResponseEntity<ReconciliationFormulaResponse> createFormula(
            @Valid @RequestBody CreateReconciliationFormulaRequest request) {
        log.info("Creating reconciliation formula for account: {}", request.getAccountId());
        ReconciliationFormulaResponse formula = reconciliationFormulaService.createFormula(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(formula);
    }

    /**
     * Update an existing reconciliation formula.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReconciliationFormulaResponse> updateFormula(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReconciliationFormulaRequest request) {
        log.info("Updating reconciliation formula: {}", id);
        ReconciliationFormulaResponse formula = reconciliationFormulaService.updateFormula(id, request);
        return ResponseEntity.ok(formula);
    }

    /**
     * Delete a reconciliation formula.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFormula(@PathVariable Long id) {
        log.info("Deleting reconciliation formula: {}", id);
        reconciliationFormulaService.deleteFormula(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Set a formula as the default for its account.
     */
    @PutMapping("/{id}/set-default")
    public ResponseEntity<ReconciliationFormulaResponse> setAsDefault(@PathVariable Long id) {
        log.info("Setting reconciliation formula {} as default", id);
        ReconciliationFormulaResponse formula = reconciliationFormulaService.setAsDefault(id);
        return ResponseEntity.ok(formula);
    }
}
