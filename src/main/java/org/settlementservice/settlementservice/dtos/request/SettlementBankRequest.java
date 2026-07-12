package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettlementBankRequest {
    @NotBlank(message = "Bank name is required")
    private String name;

    @NotBlank(message = "Bank code is required")
    @Pattern(regexp = "^[0-9]{3}$", message = "Bank code must be a 3-digit numeric CBN code")
    private String code;
}
