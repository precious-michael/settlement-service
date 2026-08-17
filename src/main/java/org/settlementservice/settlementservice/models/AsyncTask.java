package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.AsyncTaskStatus;
import org.settlementservice.settlementservice.enums.AsyncTaskType;

import java.time.Instant;

@Entity
@Table(name = "async_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncTask extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AsyncTaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AsyncTaskStatus status;

    @Column(nullable = false)
    private Long totalRecords;

    @Column(nullable = false)
    private Long processedRecords;

    private Long matchedCount;

    private Long unmatchedCount;

    private Long missingCount;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant completedAt;
}
