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
@Table(name = "bank_statement_row_errors")
public class BankStatementRowError extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_statement_id", nullable = false)
    private BankStatement bankStatement;

    @Column(name = "row_num", nullable = false)
    private Integer rowNumber;

    @Column(name = "raw_row", columnDefinition = "TEXT")
    private String rawRow;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
