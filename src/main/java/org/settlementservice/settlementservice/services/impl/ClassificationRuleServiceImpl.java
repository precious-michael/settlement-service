package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.request.ClassificationRuleRequest;
import org.settlementservice.settlementservice.dtos.response.ClassificationRuleResponse;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.ClassificationRule;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.ClassificationRuleRepository;
import org.settlementservice.settlementservice.services.ClassificationRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassificationRuleServiceImpl implements ClassificationRuleService {

    private final ClassificationRuleRepository classificationRuleRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public ClassificationRuleResponse create(ClassificationRuleRequest request) {
        ClassificationRule rule = new ClassificationRule();
        rule.setRegexPattern(request.getRegexPattern());
        rule.setProductType(request.getProductType());
        rule.setAccount(resolveAccount(request.getAccountId()));
        return toResponse(classificationRuleRepository.save(rule));
    }

    @Override
    public ClassificationRuleResponse getById(Long id) {
        return toResponse(findRuleOrThrow(id));
    }

    @Override
    public List<ClassificationRuleResponse> getAll() {
        return classificationRuleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ClassificationRuleResponse update(Long id, ClassificationRuleRequest request) {
        ClassificationRule rule = findRuleOrThrow(id);
        rule.setRegexPattern(request.getRegexPattern());
        rule.setProductType(request.getProductType());
        rule.setAccount(resolveAccount(request.getAccountId()));
        return toResponse(rule);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        classificationRuleRepository.delete(findRuleOrThrow(id));
    }

    private ClassificationRule findRuleOrThrow(Long id) {
        return classificationRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No classification rule found with id " + id));
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with id " + accountId));
    }

    private ClassificationRuleResponse toResponse(ClassificationRule rule) {
        ClassificationRuleResponse response = modelMapper.map(rule, ClassificationRuleResponse.class);
        if (rule.getAccount() != null) {
            response.setAccountName(rule.getAccount().getName());
        }
        return response;
    }
}
