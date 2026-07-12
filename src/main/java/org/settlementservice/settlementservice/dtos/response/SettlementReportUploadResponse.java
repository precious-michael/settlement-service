package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.BatchStatus;

@Getter
@Setter
public class SettlementReportUploadResponse {
    private Long id;
    private String fileName;
    private BatchStatus status;
}