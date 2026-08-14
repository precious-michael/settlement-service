package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.dtos.request.SelfResolutionRuleRequest;
import org.settlementservice.settlementservice.dtos.response.SelfResolutionRuleResponse;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SelfResolutionRule;
import org.settlementservice.settlementservice.repositories.SelfResolutionRuleRepository;
import org.settlementservice.settlementservice.services.SelfResolutionRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.PatternSyntaxException;

@Service
@RequiredArgsConstructor
public class SelfResolutionRuleServiceImpl implements SelfResolutionRuleService {

    private final SelfResolutionRuleRepository repository;

    @Override
    public List<SelfResolutionRuleResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SelfResolutionRuleResponse create(SelfResolutionRuleRequest request) {
        validatePattern(request.getPattern());
        SelfResolutionRule rule = new SelfResolutionRule();
        rule.setName(request.getName());
        rule.setPattern(request.getPattern());
        return toResponse(repository.save(rule));
    }

    @Override
    @Transactional
    public SelfResolutionRuleResponse update(Long id, SelfResolutionRuleRequest request) {
        SelfResolutionRule rule = findById(id);
        validatePattern(request.getPattern());
        rule.setName(request.getName());
        rule.setPattern(request.getPattern());
        return toResponse(repository.save(rule));
    }

    @Override
    @Transactional
    public void setActive(Long id, boolean active) {
        SelfResolutionRule rule = findById(id);
        rule.setActive(active);
        repository.save(rule);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }

    private SelfResolutionRule findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Self-resolution rule not found: " + id));
    }

    private void validatePattern(String pattern) {
        try {
            java.util.regex.Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
        }
    }

    private SelfResolutionRuleResponse toResponse(SelfResolutionRule rule) {
        return SelfResolutionRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .pattern(rule.getPattern())
                .active(rule.isActive())
                .createdAt(rule.getCreatedAt())
                .build();
    }
}
