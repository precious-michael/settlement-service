package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.request.CreateReconciliationFormulaRequest;
import org.settlementservice.settlementservice.dtos.response.ReconciliationFormulaResponse;
import org.settlementservice.settlementservice.dtos.request.UpdateReconciliationFormulaRequest;

import java.util.List;

/**
 * Service for managing reconciliation formulas.
 */
public interface ReconciliationFormulaService {

    /**
     * Get all formulas for an account.
     */
    List<ReconciliationFormulaResponse> getFormulasByAccount(Long accountId);

    /**
     * Get a specific formula by ID.
     */
    ReconciliationFormulaResponse getFormulaById(Long id);

    /**
     * Create a new formula.
     */
    ReconciliationFormulaResponse createFormula(CreateReconciliationFormulaRequest request);

    /**
     * Update an existing formula.
     */
    ReconciliationFormulaResponse updateFormula(Long id, UpdateReconciliationFormulaRequest request);

    /**
     * Delete a formula.
     */
    void deleteFormula(Long id);

    /**
     * Set a formula as the default for its account.
     */
    ReconciliationFormulaResponse setAsDefault(Long id);
}
