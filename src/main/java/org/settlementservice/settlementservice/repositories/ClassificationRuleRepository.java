package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.ClassificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, Long> {

    /**
     * Rules that apply to a given account: account-specific rules plus global rules (account is null).
     */
    List<ClassificationRule> findByAccountIdOrAccountIsNull(Long accountId);
}
