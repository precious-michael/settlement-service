package org.settlementservice.settlementservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.request.ClassificationRuleRequest;
import org.settlementservice.settlementservice.dtos.response.ClassificationRuleResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.ClassificationRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/classification-rules")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ClassificationRuleController {

    private final ClassificationRuleService classificationRuleService;

    @PostMapping
    public ResponseEntity<SettlementServiceResponse<ClassificationRuleResponse>> create(@Valid @RequestBody ClassificationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Classification rule created successfully", classificationRuleService.create(request)));
    }

    @GetMapping
    public SettlementServiceResponse<List<ClassificationRuleResponse>> getAll() {
        return SettlementServiceResponse.success("Classification rules retrieved successfully", classificationRuleService.getAll());
    }

    @GetMapping("/{id}")
    public SettlementServiceResponse<ClassificationRuleResponse> getById(@PathVariable Long id) {
        return SettlementServiceResponse.success("Classification rule retrieved successfully", classificationRuleService.getById(id));
    }

    @PutMapping("/{id}")
    public SettlementServiceResponse<ClassificationRuleResponse> update(@PathVariable Long id, @Valid @RequestBody ClassificationRuleRequest request) {
        return SettlementServiceResponse.success("Classification rule updated successfully", classificationRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public SettlementServiceResponse<Void> delete(@PathVariable Long id) {
        classificationRuleService.delete(id);
        return SettlementServiceResponse.success("Classification rule deleted successfully");
    }
}
