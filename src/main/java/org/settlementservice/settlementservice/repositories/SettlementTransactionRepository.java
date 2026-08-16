package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.enums.ReconciliationStatus;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, Long> {

    List<SettlementTransaction> findBySettlementReportId(Long settlementReportId);

    Page<SettlementTransaction> findBySettlementReportId(Long settlementReportId, Pageable pageable);

    List<SettlementTransaction> findByReconciliationStatus(ReconciliationStatus reconciliationStatus);

    void deleteBySettlementReportId(Long settlementReportId);
}
