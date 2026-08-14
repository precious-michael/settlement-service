package org.settlementservice.settlementservice.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ReconciliationFormulaResponse {

    private Long id;
    private Long accountId;
    private String accountName;
    private String name;
    private String formula;
    private String description;
    private boolean isDefault;
    private boolean active;
    private Instant createdAt;
}
