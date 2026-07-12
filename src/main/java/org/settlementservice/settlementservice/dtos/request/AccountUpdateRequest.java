package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.AccountStatus;

@Getter
@Setter
public class AccountUpdateRequest {
        @NotNull(message = "Status is required")
        private AccountStatus status;
}
