package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.response.TransactionResponse;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface TransactionService {

    Optional<TransactionResponse> getById(Long transactionId);

    Page<TransactionResponse> search(
            TransactionStatus status,
            Long accountId,
            ProductType productType,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable);

    Page<TransactionResponse> getByBankStatementId(Long bankStatementId, Pageable pageable);
}
