package org.settlementservice.settlementservice.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelfResolutionRuleRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String pattern;
}
