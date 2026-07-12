package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.ProductType;

@Getter
@Setter
public class ClassificationRuleRequest {
    @NotBlank(message = "Regex Pattern is required")
    private String regexPattern;

    @NotNull
    private ProductType productType;

    private Long accountId;
}
