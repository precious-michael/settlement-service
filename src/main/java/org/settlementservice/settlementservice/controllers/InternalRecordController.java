package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.repositories.InternalRecordRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/internal-records")
@RequiredArgsConstructor
public class InternalRecordController {

    private final InternalRecordRepository internalRecordRepository;

    /**
     * Create a new internal record manually (for testing)
     */
    @PostMapping
    public ResponseEntity<SettlementServiceResponse<InternalRecord>> create(@RequestBody CreateInternalRecordRequest request) {
        InternalRecord record = new InternalRecord();
        record.setReferenceNumber(request.getReferenceNumber());
        record.setRrn(request.getRrn());
        record.setStan(request.getStan());
        record.setTerminalId(request.getTerminalId());
        record.setTransactionDate(request.getTransactionDate());
        record.setTransactionTime(request.getTransactionTime() != null ? request.getTransactionTime() : LocalTime.now());
        record.setNarration(request.getNarration());
        record.setDebit(request.getDebit() != null ? request.getDebit() : BigDecimal.ZERO);
        record.setCredit(request.getCredit() != null ? request.getCredit() : BigDecimal.ZERO);
        record.setAmount(request.getCredit().subtract(request.getDebit()).abs());
        record.setCurrency(request.getCurrency() != null ? request.getCurrency() : "NGN");
        record.setStatus(request.getStatus() != null ? request.getStatus() : "SUCCESSFUL");

        InternalRecord saved = internalRecordRepository.save(record);
        return ResponseEntity.ok(SettlementServiceResponse.success("Internal record created", saved));
    }

    /**
     * Get all internal records
     */
    @GetMapping
    public ResponseEntity<SettlementServiceResponse<List<InternalRecord>>> getAll() {
        List<InternalRecord> records = internalRecordRepository.findAll();
        return ResponseEntity.ok(SettlementServiceResponse.success("Internal records retrieved", records));
    }

    /**
     * Delete all internal records (for testing/cleanup)
     */
    @DeleteMapping("/all")
    public ResponseEntity<SettlementServiceResponse<String>> deleteAll() {
        internalRecordRepository.deleteAll();
        return ResponseEntity.ok(SettlementServiceResponse.success("All internal records deleted", null));
    }

    @lombok.Data
    public static class CreateInternalRecordRequest {
        private String referenceNumber;
        private String rrn;
        private String stan;
        private String terminalId;
        private LocalDate transactionDate;
        private LocalTime transactionTime;
        private String narration;
        private BigDecimal debit;
        private BigDecimal credit;
        private String currency;
        private String status;
    }
}
