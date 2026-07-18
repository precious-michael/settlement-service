package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class AccountResponse {
    private Long id;
    private String name;
    private String accountNumber;
    private Long bankId;
    private String bankName;
    private AccountStatus status;
    private BigDecimal currentOpeningBalance;
    private String description;
    private Instant createdAt;
}
