package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.Discrepancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, Long> {

    Optional<Discrepancy> findByTransactionId(Long transactionId);

    @Query("""
            SELECT COALESCE(SUM(d.difference), 0)
            FROM Discrepancy d
            WHERE d.transaction.account.id = :accountId
            AND d.transaction.transactionDate BETWEEN :dateFrom AND :dateTo
            """)
    BigDecimal sumDifferenceByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("""
            SELECT d FROM Discrepancy d
            WHERE (:transactionId IS NULL OR d.transaction.id = :transactionId)
            AND (:type IS NULL
                 OR (:type = 'missing' AND d.matchedOn = 'No Match Found')
                 OR (:type = 'mismatched' AND d.matchedOn != 'No Match Found'))
            """)
    Page<Discrepancy> search(
            @Param("transactionId") Long transactionId,
            @Param("type") String type,
            Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM Discrepancy d
            WHERE d.settlementTransaction.settlementReport.id = :settlementReportId
            """)
    void deleteBySettlementReportId(@Param("settlementReportId") Long settlementReportId);
}
