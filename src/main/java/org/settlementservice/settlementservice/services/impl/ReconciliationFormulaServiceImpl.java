package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.exceptions.ValidationException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.dtos.request.CreateReconciliationFormulaRequest;
import org.settlementservice.settlementservice.dtos.response.ReconciliationFormulaResponse;
import org.settlementservice.settlementservice.dtos.request.UpdateReconciliationFormulaRequest;
import org.settlementservice.settlementservice.models.ReconciliationFormula;
import org.settlementservice.settlementservice.repositories.ReconciliationFormulaRepository;
import org.settlementservice.settlementservice.services.ReconciliationFormulaService;
import org.settlementservice.settlementservice.validators.ReconciliationFormulaValidator;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationFormulaServiceImpl implements ReconciliationFormulaService {

    private final ReconciliationFormulaRepository reconciliationFormulaRepository;
    private final AccountRepository accountRepository;

    @Override
    public List<ReconciliationFormulaResponse> getFormulasByAccount(Long accountId) {
        return reconciliationFormulaRepository.findByAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ReconciliationFormulaResponse getFormulaById(Long id) {
        ReconciliationFormula formula = reconciliationFormulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation formula not found: " + id));
        return toResponse(formula);
    }

    @Override
    @Transactional
    public ReconciliationFormulaResponse createFormula(CreateReconciliationFormulaRequest request) {
        // Validate formula syntax
        ReconciliationFormulaValidator.ValidationResult validation =
                ReconciliationFormulaValidator.validate(request.getFormula());
        if (!validation.isValid()) {
            throw new ValidationException("Invalid formula: " + validation.getErrorMessage());
        }

        // Check account exists
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountId()));

        // Check for duplicate name within account
        reconciliationFormulaRepository.findByAccountIdAndName(request.getAccountId(), request.getName())
                .ifPresent(existing -> {
                    throw new ValidationException("Formula with name '" + request.getName() + "' already exists for this account");
                });

        // Check if this is the first formula for this account
        List<ReconciliationFormula> existingFormulas = reconciliationFormulaRepository.findByAccountId(request.getAccountId());
        boolean isFirstFormula = existingFormulas.isEmpty();

        // If this is marked as default, or if it's the first formula, unset other defaults
        if (Boolean.TRUE.equals(request.getIsDefault()) || isFirstFormula) {
            unsetOtherDefaults(request.getAccountId());
        }

        ReconciliationFormula formula = new ReconciliationFormula();
        formula.setAccount(account);
        formula.setName(request.getName());
        formula.setFormula(request.getFormula());
        formula.setDescription(request.getDescription());
        // First formula for an account is automatically default
        formula.setDefault(Boolean.TRUE.equals(request.getIsDefault()) || isFirstFormula);
        formula.setActive(Boolean.TRUE.equals(request.getActive()));

        formula = reconciliationFormulaRepository.save(formula);
        log.info("Created reconciliation formula {} for account {}", formula.getId(), account.getId());

        return toResponse(formula);
    }

    @Override
    @Transactional
    public ReconciliationFormulaResponse updateFormula(Long id, UpdateReconciliationFormulaRequest request) {
        ReconciliationFormula formula = reconciliationFormulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation formula not found: " + id));

        // Validate formula if it's being updated
        if (request.getFormula() != null) {
            ReconciliationFormulaValidator.ValidationResult validation =
                    ReconciliationFormulaValidator.validate(request.getFormula());
            if (!validation.isValid()) {
                throw new ValidationException("Invalid formula: " + validation.getErrorMessage());
            }
            formula.setFormula(request.getFormula());
        }

        if (request.getName() != null) {
            // Check for duplicate name within account (excluding current formula)
            reconciliationFormulaRepository.findByAccountIdAndName(formula.getAccount().getId(), request.getName())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new ValidationException("Formula with name '" + request.getName() + "' already exists for this account");
                        }
                    });
            formula.setName(request.getName());
        }

        if (request.getDescription() != null) {
            formula.setDescription(request.getDescription());
        }

        if (request.getActive() != null) {
            formula.setActive(request.getActive());
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetOtherDefaults(formula.getAccount().getId());
            formula.setDefault(true);
        } else if (Boolean.FALSE.equals(request.getIsDefault())) {
            formula.setDefault(false);
        }

        formula = reconciliationFormulaRepository.save(formula);
        log.info("Updated reconciliation formula {}", id);

        return toResponse(formula);
    }

    @Override
    @Transactional
    public void deleteFormula(Long id) {
        ReconciliationFormula formula = reconciliationFormulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation formula not found: " + id));

        if (formula.isDefault()) {
            throw new ValidationException("Cannot delete default formula. Set another formula as default first.");
        }

        reconciliationFormulaRepository.delete(formula);
        log.info("Deleted reconciliation formula {}", id);
    }

    @Override
    @Transactional
    public ReconciliationFormulaResponse setAsDefault(Long id) {
        ReconciliationFormula formula = reconciliationFormulaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation formula not found: " + id));

        unsetOtherDefaults(formula.getAccount().getId());
        formula.setDefault(true);
        formula = reconciliationFormulaRepository.save(formula);

        log.info("Set reconciliation formula {} as default for account {}", id, formula.getAccount().getId());
        return toResponse(formula);
    }

    private void unsetOtherDefaults(Long accountId) {
        reconciliationFormulaRepository.findByAccountIdAndIsDefaultTrue(accountId)
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    reconciliationFormulaRepository.save(existing);
                });
    }

    private ReconciliationFormulaResponse toResponse(ReconciliationFormula formula) {
        return ReconciliationFormulaResponse.builder()
                .id(formula.getId())
                .accountId(formula.getAccount().getId())
                .accountName(formula.getAccount().getName())
                .name(formula.getName())
                .formula(formula.getFormula())
                .description(formula.getDescription())
                .isDefault(formula.isDefault())
                .active(formula.isActive())
                .createdAt(formula.getCreatedAt())
                .build();
    }
}
