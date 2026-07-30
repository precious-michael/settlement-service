package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            SELECT t FROM Transaction t
            WHERE (:status IS NULL OR t.status = :status)
            AND (:accountId IS NULL OR t.account.id = :accountId)
            AND (:productType IS NULL OR t.productType = :productType)
            AND (:dateFrom IS NULL OR t.transactionDate >= :dateFrom)
            AND (:dateTo IS NULL OR t.transactionDate <= :dateTo)
            """)
    Page<Transaction> search(
            @Param("status") TransactionStatus status,
            @Param("accountId") Long accountId,
            @Param("productType") ProductType productType,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    /**
     * Used to skip re-inserting a row that was already successfully imported in an earlier
     * upload attempt for the same account, when a corrected file is re-uploaded.
     */
    Optional<Transaction> findByAccountIdAndReferenceNumber(Long accountId, String referenceNumber);

    long countByAccountIdAndTransactionDateBetween(Long accountId, LocalDate dateFrom, LocalDate dateTo);

    long countByAccountIdAndTransactionDateBetweenAndStatus(
            Long accountId, LocalDate dateFrom, LocalDate dateTo, TransactionStatus status);
}
