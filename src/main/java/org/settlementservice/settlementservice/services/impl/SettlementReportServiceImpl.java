package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.async.SettlementReportUploadTask;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SettlementReportServiceImpl implements SettlementReportService {

    private final SettlementReportRepository settlementReportRepository;
    private final TransactionRepository transactionRepository;
    private final ModelMapper modelMapper;
    private final SettlementReportUploadTask settlementReportUploadTask;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SettlementReportUploadResponse upload(Long transactionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] fileBytes = readBytes(file);

        // Only the DB read+write is wrapped explicitly — the async trigger below must run after
        // this has committed, not from inside a method-level @Transactional that wouldn't commit
        // until the whole method (including the trigger) returns.
        SettlementReport saved = transactionTemplate.execute(status -> {
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
            return settlementReportRepository.save(settlementReport);
        });

        settlementReportUploadTask.process(saved.getId(), saved.getFileName(), fileBytes);
        return modelMapper.map(saved, SettlementReportUploadResponse.class);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
