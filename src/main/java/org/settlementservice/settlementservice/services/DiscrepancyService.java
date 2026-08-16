package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.response.DiscrepancyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DiscrepancyService {

    Page<DiscrepancyResponse> search(Long transactionId, String type, Pageable pageable);
}
