package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.SelfResolutionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SelfResolutionRuleRepository extends JpaRepository<SelfResolutionRule, Long> {

    List<SelfResolutionRule> findByActiveTrue();
}
