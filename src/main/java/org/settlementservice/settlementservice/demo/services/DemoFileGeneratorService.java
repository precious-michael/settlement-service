package org.settlementservice.settlementservice.demo.services;

import org.settlementservice.settlementservice.demo.dtos.DemoFileGenerateResponse;

public interface DemoFileGeneratorService {

    /**
     * Generates actual CSV/Excel files for testing the upload flow.
     * Creates:
     * - Bank statement CSV file
     * - Settlement report CSV file
     * - InternalRecords in database (simulating internal banking system)
     *
     * @param accountId Account to generate data for
     * @param count Number of transactions to generate
     * @param mismatchRate Percentage of transactions with amount mismatches (0.0 to 1.0)
     * @return Response with file paths and generation summary
     */
    DemoFileGenerateResponse generateFiles(Long accountId, int count, double mismatchRate);
}
