package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.TransactionResponse;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    @Override
    public Page<TransactionResponse> search(
            TransactionStatus status,
            Long accountId,
            ProductType productType,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable) {
        return transactionRepository
                .search(status, accountId, productType, dateFrom, dateTo, pageable)
                .map(this::toResponse);
    }

    @Override
    public Page<TransactionResponse> getByBankStatementId(Long bankStatementId, Pageable pageable) {
        return transactionRepository
                .findByBankStatementIdOrderByIdAsc(bankStatementId, pageable)
                .map(this::toResponse);
    }

    private TransactionResponse toResponse(Transaction t) {
        TransactionResponse response = new TransactionResponse();
        response.setId(t.getId());
        response.setBankStatementId(t.getBankStatement().getId());
        response.setAccountId(t.getAccount().getId());
        response.setTransactionDate(t.getTransactionDate());
        response.setValueDate(t.getValueDate());
        response.setNarration(t.getNarration());
        response.setReferenceNumber(t.getReferenceNumber());
        response.setDebit(t.getDebit());
        response.setCredit(t.getCredit());
        response.setBalance(t.getBalance());
        response.setProductType(t.getProductType());
        response.setStatus(t.getStatus());
        response.setCreatedAt(t.getCreatedAt());
        return response;
    }
}
