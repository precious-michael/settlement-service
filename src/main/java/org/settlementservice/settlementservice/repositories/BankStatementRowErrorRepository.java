package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.BankStatementRowError;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementRowErrorRepository extends JpaRepository<BankStatementRowError, Long> {
}
