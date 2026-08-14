package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReconciliationFormulaRequest {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotBlank(message = "Formula name is required")
    private String name;

    @NotBlank(message = "Formula is required")
    private String formula;

    private String description;

    private Boolean isDefault = false;

    private Boolean active = true;
}
