package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.SettlementReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementReportRepository extends JpaRepository<SettlementReport, Long> {

    Optional<SettlementReport> findByTransactionId(Long transactionId);

    boolean existsByTransactionId(Long transactionId);
}
