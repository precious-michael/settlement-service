package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSettlementReportFormulaRequest {

    @NotNull(message = "Formula ID is required")
    private Long formulaId;
}
