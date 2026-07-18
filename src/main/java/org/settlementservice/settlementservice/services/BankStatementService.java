package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.response.BankStatementUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface BankStatementService {

    BankStatementUploadResponse upload(Long accountId, MultipartFile file, BigDecimal openingBalance);
}
