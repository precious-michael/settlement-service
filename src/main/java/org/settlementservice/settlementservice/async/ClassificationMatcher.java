package org.settlementservice.settlementservice.async;

import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.models.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches a transaction narration against a set of classification rules. Account-specific rules
 * are tried before global ones (account == null), and the first regex match wins.
 */
@Component
public class ClassificationMatcher {

    public Optional<ProductType> classify(String narration, List<ClassificationRule> rules) {
        if (narration == null || rules == null || rules.isEmpty()) {
            return Optional.empty();
        }

        return rules.stream()
                .sorted(Comparator.comparing(rule -> rule.getAccount() == null))
                .filter(rule -> matches(rule.getRegexPattern(), narration))
                .map(ClassificationRule::getProductType)
                .findFirst();
    }

    private boolean matches(String regexPattern, String narration) {
        try {
            return Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE).matcher(narration).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
