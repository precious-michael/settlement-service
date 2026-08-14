package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    Optional<BankStatement> findByAccountIdAndFileHash(Long accountId, String fileHash);

    /**
     * Find the most recent bank statement for an account (by upload date).
     * Used to validate opening balance against previous closing balance.
     */
    @Query("SELECT bs FROM BankStatement bs WHERE bs.account.id = :accountId " +
           "ORDER BY bs.uploadDate DESC LIMIT 1")
    Optional<BankStatement> findLatestByAccountId(Long accountId);

    /**
     * Find all bank statements for an account, ordered by upload date descending.
     */
    List<BankStatement> findByAccountIdOrderByUploadDateDesc(Long accountId);
}
