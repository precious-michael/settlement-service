package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.Discrepancy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, Long> {
}
