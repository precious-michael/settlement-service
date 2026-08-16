package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.DiscrepancyResponse;
import org.settlementservice.settlementservice.models.Discrepancy;
import org.settlementservice.settlementservice.repositories.DiscrepancyRepository;
import org.settlementservice.settlementservice.services.DiscrepancyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiscrepancyServiceImpl implements DiscrepancyService {

    private final DiscrepancyRepository discrepancyRepository;

    @Override
    public Page<DiscrepancyResponse> search(Long transactionId, String type, Pageable pageable) {
        return discrepancyRepository.search(transactionId, type, pageable).map(this::toResponse);
    }

    private DiscrepancyResponse toResponse(Discrepancy d) {
        DiscrepancyResponse response = new DiscrepancyResponse();
        response.setId(d.getId());
        // Transaction can be null, settlement transaction is always set
        response.setTransactionId(d.getTransaction() != null ? d.getTransaction().getId() : null);
        response.setSettlementTransactionId(d.getSettlementTransaction().getId());

        // Format amounts with DR/CR indicator (no zeros!)
        response.setInternalRecord(formatAmount(d.getExpectedDebit(), d.getExpectedCredit()));
        response.setSettlementReport(formatAmount(d.getReportedDebit(), d.getReportedCredit()));
        response.setDifference(String.format("₦%,.2f", d.getDifference()));
        response.setMatchedOn(d.getMatchedOn());

        response.setCreatedAt(d.getCreatedAt());
        return response;
    }

    /**
     * Formats debit/credit amounts as a single clean string with DR/CR indicator.
     * Examples: "₦100.00 DR", "₦50.00 CR"
     */
    private String formatAmount(java.math.BigDecimal debit, java.math.BigDecimal credit) {
        // One will always be zero, show the non-zero one
        if (debit.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return String.format("₦%,.2f DR", debit);
        } else if (credit.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return String.format("₦%,.2f CR", credit);
        } else {
            // Both zero (shouldn't happen, but handle gracefully)
            return "₦0.00";
        }
    }
}
