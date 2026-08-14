package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.enums.ReportReconciliationStatus;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementReportRepository extends JpaRepository<SettlementReport, Long> {

    Optional<SettlementReport> findByTransactionId(Long transactionId);

    boolean existsByTransactionId(Long transactionId);

    List<SettlementReport> findByReconciliationStatus(ReportReconciliationStatus status);

    @Query("""
            SELECT COUNT(r) FROM SettlementReport r
            WHERE r.account.id = :accountId
            AND r.transaction.transactionDate BETWEEN :dateFrom AND :dateTo
            AND r.reconciliationStatus = :status
            """)
    long countByAccountIdAndTransactionDateBetweenAndReconciliationStatus(
            @Param("accountId") Long accountId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("status") ReportReconciliationStatus status);
}
