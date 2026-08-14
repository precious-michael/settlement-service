package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class DiscrepancyResponse {

    private Long id;
    private Long transactionId;
    private Long settlementTransactionId;

    // Clean formatted amounts with DR/CR indicator
    private String internalRecord;     // e.g., "₦100.00 DR" or "₦100.00 CR"
    private String settlementReport;   // e.g., "₦99.50 DR" or "₦99.50 CR"
    private String difference;         // e.g., "₦0.50"

    // What fields were matched on (e.g., "RRN: ABC123, STAN: 456789")
    private String matchedOn;

    private Instant createdAt;
}
