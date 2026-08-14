package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.ProductType;

import java.time.Instant;

@Getter
@Setter
public class ClassificationRuleResponse {
    private Long id;
    private String regexPattern;
    private ProductType productType;
    private Long accountId;
    private String accountName;
    private Instant createdAt;

}
