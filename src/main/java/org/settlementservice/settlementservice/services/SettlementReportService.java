package org.settlementservice.settlementservice.services;

import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SettlementReportService {

    SettlementReportUploadResponse upload(Long transactionId, MultipartFile file);
}
