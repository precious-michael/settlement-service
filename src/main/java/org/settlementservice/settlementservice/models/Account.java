package org.settlementservice.settlementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.AccountStatus;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private SettlementBank settlementBank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal currentOpeningBalance;

    @Column(length = 500)
    private String description;
}
