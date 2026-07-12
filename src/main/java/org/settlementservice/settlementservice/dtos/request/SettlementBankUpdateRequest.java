package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.SettlementBankStatus;

@Getter
@Setter
public class SettlementBankUpdateRequest {
    private String name;
    private SettlementBankStatus status;
}
