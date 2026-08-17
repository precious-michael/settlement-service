package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.enums.ReconciliationStatus;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, Long> {

    List<SettlementTransaction> findBySettlementReportId(Long settlementReportId);

    Page<SettlementTransaction> findBySettlementReportId(Long settlementReportId, Pageable pageable);

    List<SettlementTransaction> findByReconciliationStatus(ReconciliationStatus reconciliationStatus);

    void deleteBySettlementReportId(Long settlementReportId);

    @Query(value = "SELECT st.id as transactionId, sr.account_id as accountId " +
                   "FROM settlement_transactions st " +
                   "JOIN settlement_reports sr ON st.settlement_report_id = sr.id " +
                   "WHERE st.id IN (:ids)", nativeQuery = true)
    List<Object[]> findTransactionAccountMapping(@Param("ids") List<Long> ids);

    @Query("SELECT st FROM SettlementTransaction st " +
           "JOIN FETCH st.settlementReport sr " +
           "JOIN FETCH sr.account " +
           "JOIN FETCH sr.transaction " +
           "WHERE st.id IN (:ids)")
    List<SettlementTransaction> findAllByIdWithRelationships(@Param("ids") List<Long> ids);
}
