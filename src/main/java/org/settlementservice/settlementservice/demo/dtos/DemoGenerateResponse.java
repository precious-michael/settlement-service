package org.settlementservice.settlementservice.demo.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DemoGenerateResponse {
    private Long accountId;
    private int generated;
    private int matchedPairs;
    private int mismatchedPairs;
}
