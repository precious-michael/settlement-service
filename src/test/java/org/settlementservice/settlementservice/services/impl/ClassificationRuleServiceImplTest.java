package org.settlementservice.settlementservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.settlementservice.settlementservice.dtos.request.ClassificationRuleRequest;
import org.settlementservice.settlementservice.dtos.response.ClassificationRuleResponse;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.ClassificationRule;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.ClassificationRuleRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificationRuleServiceImplTest {

    @Mock
    private ClassificationRuleRepository classificationRuleRepository;

    @Mock
    private AccountRepository accountRepository;

    private ClassificationRuleServiceImpl classificationRuleService;

    @BeforeEach
    void setUp() {
        classificationRuleService = new ClassificationRuleServiceImpl(
                classificationRuleRepository, accountRepository, new ModelMapper());
    }

    private ClassificationRuleRequest requestFor(String regex, ProductType productType, Long accountId) {
        ClassificationRuleRequest request = new ClassificationRuleRequest();
        request.setRegexPattern(regex);
        request.setProductType(productType);
        request.setAccountId(accountId);
        return request;
    }

    @Test
    void create_globalRule_withNullAccountId_savesWithNoAccount() {
        when(classificationRuleRepository.save(any(ClassificationRule.class))).thenAnswer(invocation -> {
            ClassificationRule rule = invocation.getArgument(0);
            rule.setId(1L);
            return rule;
        });

        ClassificationRuleResponse response = classificationRuleService.create(
                requestFor(".*CARD SETTLEMENT.*", ProductType.CARD_SETTLEMENT, null));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAccountId()).isNull();
        assertThat(response.getProductType()).isEqualTo(ProductType.CARD_SETTLEMENT);
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void create_accountScopedRule_resolvesAccountAndMapsAccountId() {
        Account account = new Account();
        account.setId(5L);
        when(accountRepository.findById(5L)).thenReturn(Optional.of(account));
        when(classificationRuleRepository.save(any(ClassificationRule.class))).thenAnswer(invocation -> {
            ClassificationRule rule = invocation.getArgument(0);
            rule.setId(2L);
            return rule;
        });

        ClassificationRuleResponse response = classificationRuleService.create(
                requestFor(".*TRANSFER.*", ProductType.TRANSFER, 5L));

        assertThat(response.getAccountId()).isEqualTo(5L);
    }

    @Test
    void create_accountNotFound_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classificationRuleService.create(requestFor(".*X.*", ProductType.OTHERS, 99L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(classificationRuleRepository, never()).save(any());
    }

    @Test
    void getById_missingRule_throwsResourceNotFoundException() {
        when(classificationRuleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classificationRuleService.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_existingRule_changesFieldsAndReResolvesAccount() {
        ClassificationRule rule = new ClassificationRule();
        rule.setId(1L);
        rule.setRegexPattern(".*OLD.*");
        rule.setProductType(ProductType.OTHERS);
        when(classificationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));

        ClassificationRuleResponse response = classificationRuleService.update(
                1L, requestFor(".*NEW.*", ProductType.PAYROLL, null));

        assertThat(response.getRegexPattern()).isEqualTo(".*NEW.*");
        assertThat(response.getProductType()).isEqualTo(ProductType.PAYROLL);
        assertThat(response.getAccountId()).isNull();
    }

    @Test
    void delete_existingRule_deletesIt() {
        ClassificationRule rule = new ClassificationRule();
        rule.setId(1L);
        when(classificationRuleRepository.findById(1L)).thenReturn(Optional.of(rule));

        classificationRuleService.delete(1L);

        verify(classificationRuleRepository).delete(rule);
    }

    @Test
    void delete_missingRule_throwsResourceNotFoundException() {
        when(classificationRuleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classificationRuleService.delete(1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
