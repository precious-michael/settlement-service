package org.settlementservice.settlementservice.repositories;

import org.settlementservice.settlementservice.models.InternalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for InternalRecord - global transaction records.
 * Since InternalRecord is now independent of accounts, queries don't filter by accountId.
 */
public interface InternalRecordRepository extends JpaRepository<InternalRecord, Long> {

    Optional<InternalRecord> findFirstByReferenceNumber(String referenceNumber);

    Optional<InternalRecord> findFirstByRrn(String rrn);

    Optional<InternalRecord> findFirstByRrnAndStan(String rrn, String stan);

    Optional<InternalRecord> findFirstByTerminalIdAndTransactionDate(String terminalId, LocalDate transactionDate);

    Optional<InternalRecord> findByPan(String pan);

    Optional<InternalRecord> findByProcessorReference(String processorReference);
}
