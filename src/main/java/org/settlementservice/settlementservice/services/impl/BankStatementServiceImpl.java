package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.async.BankStatementUploadTask;
import org.settlementservice.settlementservice.dtos.response.BankStatementUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.services.BankStatementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {

    private final BankStatementRepository bankStatementRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;
    private final BankStatementUploadTask bankStatementUploadTask;
    private final TransactionTemplate transactionTemplate;

    @Override
    public BankStatementUploadResponse upload(Long accountId, MultipartFile file, BigDecimal openingBalance) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] fileBytes = readBytes(file);
        String fileHash = sha256Hex(fileBytes);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with id " + accountId));

        if (bankStatementRepository.findByAccountIdAndFileHash(accountId, fileHash).isPresent()) {
            throw new DuplicateResourceException(
                    "This file has already been uploaded for account " + accountId);
        }

        // Validate opening balance against previous statement's closing balance
        Optional<BankStatement> previousStatement = bankStatementRepository.findLatestByAccountId(accountId);
        if (previousStatement.isPresent()) {
            BigDecimal expectedOpeningBalance = previousStatement.get().getClosingBalance();
            if (expectedOpeningBalance != null && openingBalance.compareTo(expectedOpeningBalance) != 0) {
                throw new IllegalArgumentException(
                        String.format("Opening balance (%.2f) does not match previous statement's closing balance (%.2f)",
                                openingBalance, expectedOpeningBalance));
            }
        }
        // If no previous statement exists, this is the first upload - any opening balance is acceptable

        BankStatement bankStatement = BankStatement.builder()
                .account(account)
                .fileName(file.getOriginalFilename())
                .fileHash(fileHash)
                .uploadDate(Instant.now())
                .status(BatchStatus.PENDING)
                .totalEntries(0)
                .openingBalance(openingBalance)
                .build();
        BankStatement saved = bankStatementRepository.save(bankStatement);

        bankStatementUploadTask.process(saved.getId(), saved.getFileName(), fileBytes);
        return toResponse(saved);
    }

    @Override
    public BankStatementUploadResponse getStatus(Long id) {
        BankStatement bankStatement = bankStatementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank statement not found: " + id));
        return toResponse(bankStatement);
    }

    @Override
    public List<BankStatementUploadResponse> getByAccountId(Long accountId) {
        // Verify account exists
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with id " + accountId));

        List<BankStatement> bankStatements = bankStatementRepository.findByAccountIdOrderByUploadDateDesc(accountId);
        return bankStatements.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private BankStatementUploadResponse toResponse(BankStatement bankStatement) {
        return modelMapper.map(bankStatement, BankStatementUploadResponse.class);
    }

    private String sha256Hex(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(fileBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
