package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.response.BankStatementUploadResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.services.BankStatementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {

    private final BankStatementRepository bankStatementRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public BankStatementUploadResponse upload(Long accountId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with id " + accountId));

        String fileHash = sha256Hex(file);

        Optional<BankStatement> existing = bankStatementRepository.findByAccountIdAndFileHash(accountId, fileHash);
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        BankStatement bankStatement = new BankStatement();
        bankStatement.setAccount(account);
        bankStatement.setFileName(file.getOriginalFilename());
        bankStatement.setFileHash(fileHash);
        bankStatement.setUploadDate(Instant.now());
        bankStatement.setStatus(BatchStatus.PENDING);

        return toResponse(bankStatementRepository.save(bankStatement), false);
    }

    private BankStatementUploadResponse toResponse(BankStatement bankStatement, boolean duplicate) {
        BankStatementUploadResponse response = modelMapper.map(bankStatement, BankStatementUploadResponse.class);
        response.setDuplicate(duplicate);
        return response;
    }

    private String sha256Hex(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(file.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
    }
}
