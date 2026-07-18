package org.settlementservice.settlementservice.async;

import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.ClassificationRule;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationMatcherTest {

    private final ClassificationMatcher matcher = new ClassificationMatcher();

    @Test
    void classify_narrationMatchesGlobalRule_returnsItsProductType() {
        ClassificationRule rule = globalRule(".*CARD SETTLEMENT.*", ProductType.CARD_SETTLEMENT);

        Optional<ProductType> result = matcher.classify("CARD SETTLEMENT FEE", List.of(rule));

        assertThat(result).contains(ProductType.CARD_SETTLEMENT);
    }

    @Test
    void classify_noRuleMatches_returnsEmpty() {
        ClassificationRule rule = globalRule(".*PAYROLL.*", ProductType.PAYROLL);

        Optional<ProductType> result = matcher.classify("RANDOM NARRATION", List.of(rule));

        assertThat(result).isEmpty();
    }

    @Test
    void classify_matchingIsCaseInsensitiveAndDoesNotRequireFullMatch() {
        ClassificationRule rule = globalRule("transfer", ProductType.TRANSFER);

        Optional<ProductType> result = matcher.classify("outbound TRANSFER to supplier", List.of(rule));

        assertThat(result).contains(ProductType.TRANSFER);
    }

    @Test
    void classify_accountSpecificRuleWinsOverGlobalRuleWhenBothMatch() {
        ClassificationRule global = globalRule(".*TRANSFER.*", ProductType.OTHERS);
        ClassificationRule accountSpecific = accountRule(".*TRANSFER.*", ProductType.TRANSFER, 5L);

        Optional<ProductType> result = matcher.classify("TRANSFER OUT", List.of(global, accountSpecific));

        assertThat(result).contains(ProductType.TRANSFER);
    }

    @Test
    void classify_invalidRegexIsIgnoredRatherThanThrowing() {
        ClassificationRule invalid = globalRule("[unterminated", ProductType.LOAN_REPAYMENT);
        ClassificationRule valid = globalRule(".*PAYROLL.*", ProductType.PAYROLL);

        Optional<ProductType> result = matcher.classify("PAYROLL RUN", List.of(invalid, valid));

        assertThat(result).contains(ProductType.PAYROLL);
    }

    @Test
    void classify_emptyRuleList_returnsEmpty() {
        assertThat(matcher.classify("ANYTHING", List.of())).isEmpty();
    }

    private ClassificationRule globalRule(String regex, ProductType productType) {
        ClassificationRule rule = new ClassificationRule();
        rule.setRegexPattern(regex);
        rule.setProductType(productType);
        rule.setAccount(null);
        return rule;
    }

    private ClassificationRule accountRule(String regex, ProductType productType, Long accountId) {
        ClassificationRule rule = new ClassificationRule();
        rule.setRegexPattern(regex);
        rule.setProductType(productType);
        Account account = new Account();
        account.setId(accountId);
        rule.setAccount(account);
        return rule;
    }
}
