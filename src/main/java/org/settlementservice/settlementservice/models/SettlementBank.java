package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.SettlementBankStatus;

@Getter
@Setter
@Entity
@Table(name = "settlement_banks")
public class SettlementBank extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SettlementBankStatus status = SettlementBankStatus.ACTIVE;
}
