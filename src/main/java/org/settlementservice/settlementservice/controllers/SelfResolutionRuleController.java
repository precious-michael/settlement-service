package org.settlementservice.settlementservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.request.SelfResolutionRuleRequest;
import org.settlementservice.settlementservice.dtos.response.SelfResolutionRuleResponse;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.services.SelfResolutionRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/self-resolution/rules")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SelfResolutionRuleController {

    private final SelfResolutionRuleService ruleService;

    @GetMapping
    public ResponseEntity<SettlementServiceResponse<List<SelfResolutionRuleResponse>>> list() {
        return ResponseEntity.ok(SettlementServiceResponse.success("Rules retrieved", ruleService.listAll()));
    }

    @PostMapping
    public ResponseEntity<SettlementServiceResponse<SelfResolutionRuleResponse>> create(
            @Valid @RequestBody SelfResolutionRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SettlementServiceResponse.success("Rule created", ruleService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SettlementServiceResponse<SelfResolutionRuleResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SelfResolutionRuleRequest request) {
        return ResponseEntity.ok(SettlementServiceResponse.success("Rule updated", ruleService.update(id, request)));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<SettlementServiceResponse<Void>> setActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        ruleService.setActive(id, active);
        return ResponseEntity.ok(SettlementServiceResponse.success(active ? "Rule activated" : "Rule deactivated", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
