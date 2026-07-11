package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.BatchStatus;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "settlement_reports")
public class SettlementReport extends BaseEntity {

    /**
     * One settlement report per transaction, enforced via a unique FK column.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "upload_date", nullable = false)
    private Instant uploadDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BatchStatus status;

    @Column(name = "total_entries")
    private Integer totalEntries = 0;

    @Column(name = "processed_entries")
    private Integer processedEntries = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
