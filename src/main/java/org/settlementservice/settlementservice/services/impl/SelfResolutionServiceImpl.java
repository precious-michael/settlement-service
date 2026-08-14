package org.settlementservice.settlementservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.SelfResolutionRule;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.SelfResolutionRuleRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.services.SelfResolutionService;
import org.settlementservice.settlementservice.services.SettlementValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelfResolutionServiceImpl implements SelfResolutionService {

    private final TransactionRepository transactionRepository;
    private final SettlementReportRepository settlementReportRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final SettlementValidationService settlementValidationService;
    private final SelfResolutionRuleRepository ruleRepository;

    // Lazy self-reference so resolve() can call resolveOne() through the proxy,
    // ensuring each transaction resolves in its own @Transactional scope.
    @Autowired
    @Lazy
    private SelfResolutionService self;

    @Override
    public int resolve(Long accountId, Long statementId) {
        List<Transaction> candidates;
        if (accountId != null) {
            candidates = transactionRepository.findByStatusAndAccountId(TransactionStatus.UNRESOLVED, accountId);
        } else if (statementId != null) {
            candidates = transactionRepository.findByStatusAndBankStatementId(TransactionStatus.UNRESOLVED, statementId);
        } else {
            candidates = transactionRepository.findByStatus(TransactionStatus.UNRESOLVED);
        }

        int count = 0;
        for (Transaction transaction : candidates) {
            try {
                if (self.resolveOne(transaction.getId())) count++;
            } catch (Exception e) {
                log.warn("Self-resolution failed for transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }
        log.info("Self-resolution complete — resolved {}/{} (accountId={}, statementId={})",
                count, candidates.size(), accountId, statementId);
        return count;
    }

    @Override
    @Transactional
    public boolean resolveOne(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (transaction.getStatus() != TransactionStatus.UNRESOLVED) {
            return false;
        }

        String narration = transaction.getNarration();
        if (narration == null) {
            return false;
        }

        List<CompiledRule> rules = loadRules();
        if (rules.isEmpty()) {
            return false;
        }

        for (CompiledRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(narration);
            if (matcher.find()) {
                createSettlementData(transaction, matcher, rule.name());
                return true;
            }
        }
        return false;
    }

    private List<CompiledRule> loadRules() {
        return ruleRepository.findByActiveTrue().stream()
                .flatMap(rule -> {
                    try {
                        return java.util.stream.Stream.of(
                                new CompiledRule(rule.getName(), Pattern.compile(rule.getPattern())));
                    } catch (Exception e) {
                        log.warn("Skipping self-resolution rule '{}' — invalid regex: {}", rule.getName(), e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
    }

    private void createSettlementData(Transaction transaction, Matcher matcher, String ruleName) {
        SettlementReport report = new SettlementReport();
        report.setTransaction(transaction);
        report.setAccount(transaction.getAccount());
        report.setFileName("auto-resolved");
        report.setUploadDate(Instant.now());
        report.setStatus(BatchStatus.COMPLETED);
        report.setTotalEntries(1);
        report = settlementReportRepository.save(report);

        SettlementTransaction settlementTransaction = new SettlementTransaction();
        settlementTransaction.setSettlementReport(report);
        settlementTransaction.setTransactionDate(transaction.getTransactionDate());
        settlementTransaction.setNarration(transaction.getNarration());
        settlementTransaction.setTransactionReference(extractOrDefault(matcher, "ref",
                transaction.getReferenceNumber() != null
                        ? transaction.getReferenceNumber()
                        : "AUTO-" + transaction.getId()));
        settlementTransaction.setRrn(extract(matcher, "rrn"));
        settlementTransaction.setStan(extract(matcher, "stan"));
        settlementTransaction.setTerminalId(extract(matcher, "terminalId"));
        settlementTransaction.setDebit(transaction.getDebit());
        settlementTransaction.setCredit(transaction.getCredit());
        settlementTransactionRepository.save(settlementTransaction);

        log.info("Transaction {} self-resolved via rule '{}'", transaction.getId(), ruleName);
        settlementValidationService.validateSettlement(report.getId());
    }

    private String extract(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extractOrDefault(Matcher matcher, String groupName, String defaultValue) {
        String value = extract(matcher, groupName);
        return value != null ? value : defaultValue;
    }

    private record CompiledRule(String name, Pattern pattern) {}
}
