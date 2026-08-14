package org.settlementservice.settlementservice.dtos.request;

import lombok.Data;

@Data
public class UpdateReconciliationFormulaRequest {

    private String name;

    private String formula;

    private String description;

    private Boolean isDefault;

    private Boolean active;
}
