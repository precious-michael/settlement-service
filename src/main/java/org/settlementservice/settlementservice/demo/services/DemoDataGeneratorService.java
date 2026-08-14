package org.settlementservice.settlementservice.demo.services;

import org.settlementservice.settlementservice.demo.dtos.DemoGenerateResponse;

public interface DemoDataGeneratorService {

    /**
     * Creates {@code count} synthetic Transaction + InternalRecord pairs for the given account,
     * ready for the reconciliation engine to process. The first {@code round(count * mismatchRate)}
     * pairs are intentionally mismatched (InternalRecord amount is 10% lower).
     */
    DemoGenerateResponse generate(Long accountId, int count, double mismatchRate);
}
