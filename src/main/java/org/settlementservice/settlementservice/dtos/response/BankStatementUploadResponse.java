package org.settlementservice.settlementservice.dtos.response;

import lombok.Getter;
import lombok.Setter;
import org.settlementservice.settlementservice.enums.BatchStatus;

@Getter
@Setter
public class BankStatementUploadResponse{
    private Long id;
    private String fileName;
    private BatchStatus status;
    private boolean duplicate;
}