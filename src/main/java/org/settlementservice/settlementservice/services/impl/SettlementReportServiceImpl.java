package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.SettlementReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SettlementReportServiceImpl implements SettlementReportService {

    private final SettlementReportRepository settlementReportRepository;
    private final TransactionRepository transactionRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public SettlementReportUploadResponse upload(Long transactionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("No transaction found with id " + transactionId));

        if (settlementReportRepository.existsByTransactionId(transactionId)) {
            throw new DuplicateResourceException(
                    "A settlement report already exists for transaction " + transactionId);
        }

        SettlementReport settlementReport = new SettlementReport();
        settlementReport.setTransaction(transaction);
        settlementReport.setAccount(transaction.getAccount());
        settlementReport.setFileName(file.getOriginalFilename());
        settlementReport.setUploadDate(Instant.now());
        settlementReport.setStatus(BatchStatus.PENDING);

        return modelMapper.map(settlementReportRepository.save(settlementReport), SettlementReportUploadResponse.class);
    }
}
