package org.settlementservice.settlementservice.dtos.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SelfResolutionRuleResponse {

    private Long id;
    private String name;
    private String pattern;
    private boolean active;
    private Instant createdAt;
}
