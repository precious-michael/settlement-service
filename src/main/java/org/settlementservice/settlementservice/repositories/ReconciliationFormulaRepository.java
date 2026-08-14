package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.ReconciliationFormula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReconciliationFormulaRepository extends JpaRepository<ReconciliationFormula, Long> {

    List<ReconciliationFormula> findByAccountId(Long accountId);

    List<ReconciliationFormula> findByAccountIdAndActiveTrue(Long accountId);

    Optional<ReconciliationFormula> findByAccountIdAndIsDefaultTrue(Long accountId);

    Optional<ReconciliationFormula> findByAccountIdAndName(Long accountId, String name);
}
