package org.settlementservice.settlementservice.controllers;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.response.SettlementReportUploadResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementTransactionResponse;
import org.settlementservice.settlementservice.dtos.request.UpdateSettlementReportFormulaRequest;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.services.SettlementReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settlement-reports")
@RequiredArgsConstructor
public class SettlementReportController {

    private final SettlementReportService settlementReportService;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final ModelMapper modelMapper;

    @PostMapping("/upload")
    public ResponseEntity<SettlementServiceResponse<SettlementReportUploadResponse>> upload(
            @RequestParam("transactionId") Long transactionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "formulaId", required = false) Long formulaId) {
        SettlementReportUploadResponse response = settlementReportService.upload(transactionId, file, formulaId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Settlement report uploaded successfully", response));
    }

    /**
     * Get the current status of an uploaded settlement report.
     * Useful for tracking async file processing progress.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SettlementServiceResponse<SettlementReportUploadResponse>> getStatus(
            @PathVariable Long id) {
        SettlementReportUploadResponse response = settlementReportService.getStatus(id);
        return ResponseEntity.ok(SettlementServiceResponse.success("Settlement report status retrieved", response));
    }

    /**
     * Update the reconciliation formula for a settlement report.
     * This will recompute all reconciliation references and reset reconciliation status to PENDING.
     */
    @PutMapping("/{reportId}/reconciliation-formula")
    public ResponseEntity<SettlementServiceResponse<Void>> updateReconciliationFormula(
            @PathVariable Long reportId,
            @Valid @RequestBody UpdateSettlementReportFormulaRequest request) {
        settlementReportService.updateReconciliationFormula(reportId, request.getFormulaId());
        return ResponseEntity.ok(SettlementServiceResponse.success("Reconciliation formula updated successfully", null));
    }

    /**
     * Get settlement report by transaction ID.
     */
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<SettlementServiceResponse<SettlementReportUploadResponse>> getByTransactionId(
            @PathVariable Long transactionId) {
        SettlementReportUploadResponse response = settlementReportService.getByTransactionId(transactionId);
        return ResponseEntity.ok(SettlementServiceResponse.success("Settlement report retrieved", response));
    }

    /**
     * Get all settlement transactions for a settlement report.
     * Returns all transactions (not paginated) for backward compatibility.
     * Use /transactions/paged for pagination.
     */
    @GetMapping("/{reportId}/transactions")
    public ResponseEntity<SettlementServiceResponse<List<SettlementTransactionResponse>>> getTransactions(
            @PathVariable Long reportId) {
        List<SettlementTransactionResponse> transactions = settlementTransactionRepository
                .findBySettlementReportId(reportId)
                .stream()
                .map(t -> {
                    SettlementTransactionResponse response = modelMapper.map(t, SettlementTransactionResponse.class);
                    response.setSettlementReportId(t.getSettlementReport().getId());
                    return response;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(SettlementServiceResponse.success("Settlement transactions retrieved", transactions));
    }

    /**
     * Get settlement transactions for a settlement report (paginated).
     * Use this endpoint if you need pagination support.
     */
    @GetMapping("/{reportId}/transactions/paged")
    public ResponseEntity<SettlementServiceResponse<Page<SettlementTransactionResponse>>> getTransactionsPaged(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SettlementTransactionResponse> transactions = settlementTransactionRepository
                .findBySettlementReportId(reportId, PageRequest.of(page, size))
                .map(t -> {
                    SettlementTransactionResponse response = modelMapper.map(t, SettlementTransactionResponse.class);
                    response.setSettlementReportId(t.getSettlementReport().getId());
                    return response;
                });
        return ResponseEntity.ok(SettlementServiceResponse.success("Settlement transactions retrieved", transactions));
    }
}
