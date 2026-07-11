package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, Long> {

    List<SettlementTransaction> findBySettlementReportId(Long settlementReportId);
}
