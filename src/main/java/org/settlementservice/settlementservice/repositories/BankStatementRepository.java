package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    Optional<BankStatement> findByAccountIdAndFileHash(Long accountId, String fileHash);
}
