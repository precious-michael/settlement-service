package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.request.SettlementBankRequest;
import org.settlementservice.settlementservice.dtos.request.SettlementBankUpdateRequest;
import org.settlementservice.settlementservice.dtos.response.SettlementBankResponse;

import java.util.List;

public interface SettlementBankService {

    SettlementBankResponse create(SettlementBankRequest request);

    SettlementBankResponse getById(Long id);

    List<SettlementBankResponse> getAll();

    SettlementBankResponse update(Long id, SettlementBankUpdateRequest request);

}
