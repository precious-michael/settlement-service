package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.SettlementBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementBankRepository extends JpaRepository<SettlementBank, Long> {

    Optional<SettlementBank> findByName(String name);

    Optional<SettlementBank> findByCode(String code);
}
