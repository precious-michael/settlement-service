package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "settlement_report_row_errors")
public class SettlementReportRowError extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_report_id", nullable = false)
    private SettlementReport settlementReport;

    @Column(name = "row_num", nullable = false)
    private Integer rowNumber;

    @Column(name = "raw_row", columnDefinition = "TEXT")
    private String rawRow;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
