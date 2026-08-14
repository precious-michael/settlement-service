package org.settlementservice.settlementservice.demo.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.demo.dtos.DemoGenerateResponse;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.InternalRecordRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.settlementservice.settlementservice.demo.services.DemoDataGeneratorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataGeneratorServiceImpl implements DemoDataGeneratorService {

    private final AccountRepository accountRepository;
    private final BankStatementRepository bankStatementRepository;
    private final TransactionRepository transactionRepository;
    private final SettlementReportRepository settlementReportRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final InternalRecordRepository internalRecordRepository;

    @Override
    @Transactional
    public DemoGenerateResponse generate(Long accountId, int count, double mismatchRate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        BankStatement statement = BankStatement.builder()
                .account(account)
                .fileName("demo-batch")
                .fileHash(UUID.randomUUID().toString().replace("-", ""))
                .uploadDate(Instant.now())
                .status(BatchStatus.COMPLETED)
                .totalEntries(count)
                .openingBalance(BigDecimal.ZERO)
                .closingBalance(BigDecimal.ZERO)
                .build();
        statement = bankStatementRepository.save(statement);

        int mismatchCount = (int) Math.round(count * mismatchRate);

        for (int i = 0; i < count; i++) {
            String ref = "DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            BigDecimal amount = new BigDecimal(10000 + (i * 1000));
            LocalDate txDate = LocalDate.now().minusDays(count - i);
            boolean isMismatch = i < mismatchCount;

            Transaction transaction = new Transaction();
            transaction.setBankStatement(statement);
            transaction.setAccount(account);
            transaction.setTransactionDate(txDate);
            transaction.setReferenceNumber(ref);
            transaction.setNarration("DEMO TRANSACTION " + (i + 1));
            transaction.setDebit(BigDecimal.ZERO);
            transaction.setCredit(amount);
            transaction.setBalance(amount);
            transaction.setStatus(TransactionStatus.RESOLVED);
            transaction = transactionRepository.save(transaction);

            // Create a settlement report so the reconciliation engine has something to process.
            SettlementReport report = new SettlementReport();
            report.setTransaction(transaction);
            report.setAccount(account);
            report.setFileName("demo-settlement");
            report.setUploadDate(Instant.now());
            report.setStatus(BatchStatus.COMPLETED);
            report.setTotalEntries(1);
            report = settlementReportRepository.save(report);

            SettlementTransaction settlementTx = new SettlementTransaction();
            settlementTx.setSettlementReport(report);
            settlementTx.setTransactionDate(txDate);
            settlementTx.setNarration("DEMO SETTLEMENT " + (i + 1));
            settlementTx.setTransactionReference(ref);
            settlementTx.setDebit(BigDecimal.ZERO);
            settlementTx.setCredit(amount);
            settlementTransactionRepository.save(settlementTx);

            // Internal record: mismatched pairs use 90% of the bank amount.
            BigDecimal internalAmount = isMismatch
                    ? amount.multiply(new BigDecimal("0.90")).setScale(4, RoundingMode.HALF_UP)
                    : amount;

            InternalRecord record = new InternalRecord();
            record.setReferenceNumber(ref);
            record.setTransactionDate(txDate);
            record.setNarration("INTERNAL RECORD " + (i + 1));
            record.setDebit(BigDecimal.ZERO);
            record.setCredit(internalAmount);
            // InternalRecord is now global - no account linkage needed
            internalRecordRepository.save(record);
        }

        log.info("Demo data generated — account={} count={} mismatches={}", accountId, count, mismatchCount);

        return DemoGenerateResponse.builder()
                .accountId(accountId)
                .generated(count)
                .matchedPairs(count - mismatchCount)
                .mismatchedPairs(mismatchCount)
                .build();
    }
}
