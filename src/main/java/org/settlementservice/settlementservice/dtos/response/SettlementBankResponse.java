package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.SettlementBankStatus;

import java.time.Instant;

@Getter
@Setter
public class SettlementBankResponse {
    private Long id;
    private String name;
    private String code;
    private SettlementBankStatus status;
    private Instant createdAt;
}
