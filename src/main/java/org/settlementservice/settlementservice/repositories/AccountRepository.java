package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
