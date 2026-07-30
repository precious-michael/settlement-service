package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.AccountStatus;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountUpdateRequest {
        @NotNull(message = "Status is required")
        private AccountStatus status;

        private BigDecimal openingBalance;

        private String description;
}
