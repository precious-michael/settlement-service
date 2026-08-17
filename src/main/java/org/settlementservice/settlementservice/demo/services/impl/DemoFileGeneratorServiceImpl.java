package org.settlementservice.settlementservice.demo.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.async.ClassificationMatcher;
import org.settlementservice.settlementservice.demo.dtos.DemoFileGenerateResponse;
import org.settlementservice.settlementservice.demo.services.DemoFileGeneratorService;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.models.ClassificationRule;
import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.ClassificationRuleRepository;
import org.settlementservice.settlementservice.repositories.InternalRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoFileGeneratorServiceImpl implements DemoFileGeneratorService {

    private final AccountRepository accountRepository;
    private final BankStatementRepository bankStatementRepository;
    private final InternalRecordRepository internalRecordRepository;
    private final ClassificationRuleRepository classificationRuleRepository;
    private final ClassificationMatcher classificationMatcher;

    @Value("${demo.files.directory:./demo-files}")
    private String demoFilesDirectory;

    @Override
    @Transactional
    public DemoFileGenerateResponse generateFiles(Long accountId, int count, double mismatchRate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        String batchId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int mismatchCount = (int) Math.round(count * mismatchRate);

        // Load classification rules for this account (account-specific + global rules)
        List<ClassificationRule> classificationRules = classificationRuleRepository.findByAccountIdOrAccountIsNull(accountId);
        log.info("Loaded {} classification rules for account {}", classificationRules.size(), accountId);

        // Check for continuity - use previous statement's closing balance and end date
        BigDecimal openingBalance = account.getOpeningBalance();
        LocalDate startDate = LocalDate.now().minusDays(count);

        Optional<BankStatement> previousStatement = bankStatementRepository.findLatestByAccountId(accountId);
        if (previousStatement.isPresent() && previousStatement.get().getClosingBalance() != null) {
            // Continue from where the previous statement left off
            openingBalance = previousStatement.get().getClosingBalance();
            // Start 1 day after the previous statement's last transaction
            startDate = previousStatement.get().getUploadDate().atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().plusDays(1);
            log.info("Continuing from previous statement — opening balance: {}, start date: {}",
                    openingBalance, startDate);
        } else if (previousStatement.isPresent()) {
            log.warn("Previous statement exists but has no closing balance — using account opening balance instead");
        }

        // Generate transaction data
        List<DemoTransaction> transactions = new ArrayList<>();
        BigDecimal runningBalance = openingBalance;

        // First 2 transactions get settlement reports (bulk)
        // Remaining transactions use self-resolution patterns
        int settlementsToGenerate = Math.min(2, count);

        for (int i = 0; i < count; i++) {
            String ref = "DEMO-" + batchId + "-" + String.format("%04d", i + 1);
            BigDecimal amount = new BigDecimal(10000 + (i * 1000));
            LocalDate txDate = startDate.plusDays(i);
            boolean isMismatch = i < mismatchCount;
            boolean isDebit = (i % 3 == 0);
            boolean willHaveSettlementReport = (i < settlementsToGenerate);

            if (isDebit) {
                runningBalance = runningBalance.subtract(amount);
            } else {
                runningBalance = runningBalance.add(amount);
            }

            DemoTransaction tx = new DemoTransaction();
            tx.transactionDate = txDate;
            tx.valueDate = txDate;
            tx.referenceNumber = ref;
            tx.rrn = String.format("%012d", 100000000000L + i);
            tx.stan = String.format("%06d", 100000 + i);
            tx.terminalId = String.format("TERM%04d", 1000 + i);

            // Generate narration based on whether it will have settlement report or use self-resolution
            if (willHaveSettlementReport) {
                // Bulk settlement - simple narration
                tx.narration = String.format("Settlement Batch %s - Transaction %d", batchId, i + 1);
            } else {
                // Self-resolution - narration matches regex patterns
                // Rotate through 3 self-resolution patterns
                int patternIndex = i % 3;
                switch (patternIndex) {
                    case 0 -> {
                        // NIP Transfer pattern: NIP/(?<rrn>[A-Z0-9]{12})/(?<ref>[^/]+)/(?<stan>[0-9]+)
                        tx.narration = String.format("NIP/%s/%s/%s", tx.rrn, ref, tx.stan);
                    }
                    case 1 -> {
                        // Card POS pattern: (?i)POS.+TERM:(?<terminalId>[A-Z0-9]{8}).+RRN:(?<rrn>[0-9]{12})
                        tx.narration = String.format("POS Purchase TERM:%s Merchant ABC RRN:%s",
                                tx.terminalId, tx.rrn.replace("RRN", ""));
                    }
                    case 2 -> {
                        // USSD Transfer pattern: (?i)USSD.+REF:(?<ref>[A-Z0-9]++)
                        tx.narration = String.format("USSD Transfer to Account REF:%s", ref);
                    }
                }
            }

            // Classify transaction using rules from the database
            ProductType productType = classificationMatcher.classify(tx.narration, classificationRules)
                    .orElse(ProductType.OTHERS);
            tx.productType = productType.toString();
            tx.debit = isDebit ? amount : BigDecimal.ZERO;
            tx.credit = isDebit ? BigDecimal.ZERO : amount;
            tx.balance = runningBalance;
            tx.isMismatch = isMismatch;

            transactions.add(tx);
        }

        // Generate CSV files
        try {
            Path demoDir = Paths.get(demoFilesDirectory);
            Files.createDirectories(demoDir);

            String bankStatementFileName = String.format("statement-%s.csv", batchId);

            Path bankStatementPath = demoDir.resolve(bankStatementFileName);

            // Generate bank statement CSV
            String bankStatementCsv = generateBankStatementCsv(transactions, openingBalance, runningBalance);
            Files.writeString(bankStatementPath, bankStatementCsv);

            // Generate settlement reports for first 2 transactions only
            // Each settlement report has multiple mini-transactions that net to the main transaction amount
            List<String> settlementFiles = new ArrayList<>();

            for (int i = 0; i < settlementsToGenerate; i++) {
                DemoTransaction mainTx = transactions.get(i);
                // Include transaction reference in filename so it's clear which transaction this is for
                String settlementFileName = String.format("settlement-%s-FOR-%s.csv",
                        batchId, mainTx.referenceNumber);
                Path settlementPath = demoDir.resolve(settlementFileName);

                String settlementCsv = generateSettlementReportCsv(mainTx, i);
                Files.writeString(settlementPath, settlementCsv);
                settlementFiles.add(settlementFileName);
            }

            // Create internal records
            // For transactions with settlement reports: create records for mini-transactions
            // For transactions without settlement reports: create records for the main transaction
            int internalRecordsCreated = createInternalRecords(transactions, settlementsToGenerate);

            log.info("Generated demo files — account={} count={} mismatches={} bank-statement={} settlements={} internal-records={}",
                    accountId, count, mismatchCount, bankStatementFileName, settlementFiles, internalRecordsCreated);

            String settlementFilesList = settlementFiles.isEmpty() ? "None" : String.join(", ", settlementFiles);

            String instructions = String.format(
                    "Demo files generated successfully!\n\n" +
                    "Opening Balance: %.2f\n" +
                    "Closing Balance: %.2f\n" +
                    "Date Range: %s to %s\n" +
                    "Settlement Reports: %d transaction(s)\n\n" +
                    "IMPORTANT: Each settlement report contains multiple mini-transactions that net to ONE bank statement transaction.\n\n" +
                    "Next Steps:\n" +
                    "1. Upload bank statement: %s\n" +
                    "2. Get transaction IDs from the uploaded statement\n" +
                    "3. Upload settlement reports (one per transaction):\n" +
                    "   %s\n" +
                    "4. Run reconciliation to match them\n",
                    openingBalance, runningBalance,
                    startDate, startDate.plusDays(count - 1),
                    settlementFiles.size(),
                    bankStatementFileName,
                    settlementFilesList
            );

            DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("dd MMM yyyy");

            return DemoFileGenerateResponse.builder()
                    .accountId(accountId)
                    .generated(count)
                    .matchedPairs(count - mismatchCount)
                    .mismatchedPairs(mismatchCount)
                    .bankStatementFile(bankStatementFileName)
                    .settlementReportFile(settlementFiles.isEmpty() ? null : settlementFiles.get(0))
                    .settlementReportFiles(settlementFiles)
                    .bankStatementDownloadUrl("/api/demo/download/" + bankStatementFileName)
                    .settlementReportDownloadUrl(settlementFiles.isEmpty() ? null : "/api/demo/download/" + settlementFiles.get(0))
                    .transactionCount(count)
                    .internalRecordsCreated(internalRecordsCreated)
                    .openingBalance(openingBalance)
                    .closingBalance(runningBalance)
                    .dateFrom(startDate.format(displayFormat))
                    .dateTo(startDate.plusDays(count - 1).format(displayFormat))
                    .recommendedFormulas("${transactionReference} or ${rrn}/${stan}")
                    .availableFields(List.of("referenceNumber", "transactionReference", "rrn", "stan", "terminalId", "transactionDate"))
                    .instructions(instructions)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate demo files", e);
        }
    }

    /**
     * Creates internal records to match against during reconciliation.
     * - For transactions with settlement reports (first N): creates records for mini-transactions
     * - For transactions without settlement reports (rest): creates records for main transaction
     * - Transactions marked as mismatch: splits into UNRECONCILED (amount mismatch) and MISSING (no record)
     */
    private int createInternalRecords(List<DemoTransaction> transactions, int settlementsToGenerate) {
        int count = 0;
        int mismatchCount = (int) transactions.stream().filter(t -> t.isMismatch).count();
        int mismatchesProcessed = 0;

        for (int i = 0; i < transactions.size(); i++) {
            DemoTransaction mainTx = transactions.get(i);
            boolean hasSettlementReport = (i < settlementsToGenerate);

            // For mismatch transactions: split 90% UNRECONCILED (amount mismatch), 10% MISSING (no record)
            boolean shouldSkipRecord = mainTx.isMismatch && (mismatchesProcessed >= mismatchCount * 0.9);
            if (mainTx.isMismatch) {
                mismatchesProcessed++;
            }

            // Skip creating InternalRecords for "missing" transactions
            if (shouldSkipRecord) {
                log.debug("Skipping InternalRecord for transaction {} (will result in MISSING)", mainTx.referenceNumber);
                continue;
            }

            if (hasSettlementReport) {
                // Create internal records for mini-transactions (same logic as settlement report CSV)
                int miniTxCount = 3 + (i % 3); // 3, 4, or 5 mini-transactions
                BigDecimal targetNet = mainTx.credit.subtract(mainTx.debit);
                boolean isCredit = targetNet.compareTo(BigDecimal.ZERO) > 0;
                BigDecimal runningNet = BigDecimal.ZERO;

                for (int j = 0; j < miniTxCount; j++) {
                    boolean isLastRow = (j == miniTxCount - 1);
                    BigDecimal miniDebit, miniCredit;

                    if (isLastRow) {
                        BigDecimal needed = targetNet.subtract(runningNet);
                        if (needed.compareTo(BigDecimal.ZERO) >= 0) {
                            miniDebit = BigDecimal.ZERO;
                            miniCredit = needed;
                        } else {
                            miniDebit = needed.abs();
                            miniCredit = BigDecimal.ZERO;
                        }
                    } else {
                        BigDecimal portion = targetNet.abs()
                                .divide(new BigDecimal(miniTxCount), 2, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("0.8"))
                                .add(new BigDecimal(j * 500));

                        boolean shouldBeInTargetDirection = (j % 3 != 1);
                        if (isCredit && shouldBeInTargetDirection) {
                            miniDebit = BigDecimal.ZERO;
                            miniCredit = portion;
                        } else if (isCredit && !shouldBeInTargetDirection) {
                            miniDebit = portion.multiply(new BigDecimal("0.3"));
                            miniCredit = BigDecimal.ZERO;
                        } else if (!isCredit && shouldBeInTargetDirection) {
                            miniDebit = portion;
                            miniCredit = BigDecimal.ZERO;
                        } else {
                            miniDebit = BigDecimal.ZERO;
                            miniCredit = portion.multiply(new BigDecimal("0.3"));
                        }
                    }

                    runningNet = runningNet.add(miniCredit).subtract(miniDebit);

                    // Round to 2 decimal places to match CSV formatting
                    miniDebit = miniDebit.setScale(2, RoundingMode.HALF_UP);
                    miniCredit = miniCredit.setScale(2, RoundingMode.HALF_UP);

                    // Apply mismatch to internal record (settlement CSV will have different amount)
                    // This creates UNRECONCILED status during reconciliation
                    BigDecimal internalDebit = miniDebit;
                    BigDecimal internalCredit = miniCredit;
                    if (mainTx.isMismatch && j == 0) {
                        // Apply mismatch to first mini-transaction only
                        // Add small difference (0.50 to 1.50)
                        BigDecimal diff = new BigDecimal("0.50").add(new BigDecimal(i % 100).multiply(new BigDecimal("0.01")));
                        if (miniCredit.compareTo(BigDecimal.ZERO) > 0) {
                            internalCredit = internalCredit.add(diff).setScale(2, RoundingMode.HALF_UP);
                        } else if (miniDebit.compareTo(BigDecimal.ZERO) > 0) {
                            internalDebit = internalDebit.add(diff).setScale(2, RoundingMode.HALF_UP);
                        }
                    }

                    // Create internal record for this mini-transaction
                    // Use unique reference that matches the settlement report CSV
                    String uniqueRef = mainTx.referenceNumber + "-" + String.format("%02d", j + 1);

                    InternalRecord record = new InternalRecord();
                    record.setReferenceNumber(uniqueRef);
                    record.setRrn(mainTx.rrn + "-" + (j + 1));
                    record.setStan(mainTx.stan + String.format("%02d", j + 1));
                    record.setTerminalId(mainTx.terminalId);
                    record.setTransactionDate(mainTx.transactionDate);
                    record.setTransactionTime(java.time.LocalTime.of(10, 0).plusMinutes(i * 5 + j));
                    record.setNarration(String.format("Settlement item %d of %d - %s", j + 1, miniTxCount, mainTx.narration));
                    record.setDebit(internalDebit);
                    record.setCredit(internalCredit);
                    record.setAmount(internalCredit.subtract(internalDebit).abs());
                    record.setCurrency("NGN");
                    record.setStatus("SUCCESSFUL");

                    internalRecordRepository.save(record);
                    count++;
                }
            } else {
                // Create single internal record matching the main transaction
                InternalRecord record = new InternalRecord();
                record.setReferenceNumber(mainTx.referenceNumber);
                record.setRrn(mainTx.rrn);
                record.setStan(mainTx.stan);
                record.setTerminalId(mainTx.terminalId);
                record.setTransactionDate(mainTx.transactionDate);
                record.setTransactionTime(java.time.LocalTime.of(10, 0).plusMinutes(i * 5));
                record.setNarration(mainTx.narration);
                record.setDebit(mainTx.debit);
                record.setCredit(mainTx.credit);
                record.setAmount(mainTx.credit.subtract(mainTx.debit).abs());
                record.setCurrency("NGN");
                record.setStatus("SUCCESSFUL");

                internalRecordRepository.save(record);
                count++;
            }
        }

        return count;
    }

    private String generateBankStatementCsv(List<DemoTransaction> transactions, BigDecimal openingBalance, BigDecimal closingBalance) {
        StringBuilder csv = new StringBuilder();
        DateTimeFormatter isoFormat = DateTimeFormatter.ISO_LOCAL_DATE;  // yyyy-MM-dd format
        DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

        // Header block (mimics real bank statement)
        csv.append("DEMO BANK STATEMENT\n");
        csv.append(String.format("Period: %s to %s\n",
                transactions.get(0).transactionDate.format(displayFormat),
                transactions.get(transactions.size() - 1).transactionDate.format(displayFormat)));
        csv.append(String.format("Opening Balance: %.2f\n", openingBalance));
        csv.append(String.format("Closing Balance: %.2f\n", closingBalance));
        csv.append("\n");

        // Table header (parser looks for "Transaction Date" to find table start)
        csv.append("Transaction Date,Value Date,Narration,Reference Number,Debit,Credit,Balance\n");

        // Transaction rows - use ISO format (yyyy-MM-dd) for dates
        for (DemoTransaction tx : transactions) {
            csv.append(String.format("%s,%s,%s,%s,%.2f,%.2f,%.2f\n",
                    tx.transactionDate.format(isoFormat),
                    tx.valueDate.format(isoFormat),
                    tx.narration,
                    tx.referenceNumber,
                    tx.debit,
                    tx.credit,
                    tx.balance));
        }

        return csv.toString();
    }

    /**
     * Generates a settlement report CSV for ONE transaction.
     * The report contains 3-5 mini-transactions with mixed debits/credits that net to the main transaction amount.
     *
     * @param mainTx The main bank statement transaction
     * @param txIndex Index for generating unique identifiers
     * @return CSV content
     */
    private String generateSettlementReportCsv(DemoTransaction mainTx, int txIndex) {
        StringBuilder csv = new StringBuilder();
        DateTimeFormatter dateFormat = DateTimeFormatter.ISO_LOCAL_DATE;

        // Header row
        csv.append("transaction_date,settlement_date,narration,transaction_reference,debit,credit,rrn,stan,terminal_id\n");

        // Calculate target net amount (credit - debit)
        BigDecimal targetNet = mainTx.credit.subtract(mainTx.debit);
        boolean isCredit = targetNet.compareTo(BigDecimal.ZERO) > 0;

        // Generate 3-5 mini-transactions
        int miniTxCount = 3 + (txIndex % 3); // 3, 4, or 5 mini-transactions
        BigDecimal runningNet = BigDecimal.ZERO;

        for (int i = 0; i < miniTxCount; i++) {
            boolean isLastRow = (i == miniTxCount - 1);

            BigDecimal miniDebit;
            BigDecimal miniCredit;

            if (isLastRow) {
                // Last row adjusts to hit the target exactly
                BigDecimal needed = targetNet.subtract(runningNet);
                if (needed.compareTo(BigDecimal.ZERO) >= 0) {
                    miniDebit = BigDecimal.ZERO;
                    miniCredit = needed;
                } else {
                    miniDebit = needed.abs();
                    miniCredit = BigDecimal.ZERO;
                }
            } else {
                // Generate a portion of the target amount
                BigDecimal portion = targetNet.abs()
                        .divide(new BigDecimal(miniTxCount), 2, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("0.8"))  // Use 80% to leave room for adjustment
                        .add(new BigDecimal(i * 500));  // Add variation

                // Mix debits and credits (roughly 70% in target direction, 30% opposite)
                boolean shouldBeInTargetDirection = (i % 3 != 1); // Most in target direction

                if (isCredit && shouldBeInTargetDirection) {
                    miniDebit = BigDecimal.ZERO;
                    miniCredit = portion;
                } else if (isCredit && !shouldBeInTargetDirection) {
                    miniDebit = portion.multiply(new BigDecimal("0.3"));
                    miniCredit = BigDecimal.ZERO;
                } else if (!isCredit && shouldBeInTargetDirection) {
                    miniDebit = portion;
                    miniCredit = BigDecimal.ZERO;
                } else {
                    miniDebit = BigDecimal.ZERO;
                    miniCredit = portion.multiply(new BigDecimal("0.3"));
                }
            }

            runningNet = runningNet.add(miniCredit).subtract(miniDebit);

            // Generate unique identifiers for this mini-transaction
            String miniRrn = mainTx.rrn + "-" + (i + 1);
            String miniStan = mainTx.stan + String.format("%02d", i + 1);
            String miniTerminalId = mainTx.terminalId;
            String miniNarration = String.format("Settlement item %d of %d - %s", i + 1, miniTxCount, mainTx.narration);

            // Include mini-transaction index in reference to make it unique
            String uniqueRef = mainTx.referenceNumber + "-" + String.format("%02d", i + 1);

            csv.append(String.format("%s,%s,%s,%s,%.2f,%.2f,%s,%s,%s\n",
                    mainTx.transactionDate.format(dateFormat),
                    mainTx.transactionDate.format(dateFormat),
                    miniNarration,
                    uniqueRef,
                    miniDebit,
                    miniCredit,
                    miniRrn,
                    miniStan,
                    miniTerminalId));
        }

        return csv.toString();
    }

    // Internal DTO for transaction data
    private static class DemoTransaction {
        LocalDate transactionDate;
        LocalDate valueDate;
        String narration;
        String referenceNumber;
        BigDecimal debit;
        BigDecimal credit;
        BigDecimal balance;
        String rrn;
        String stan;
        String terminalId;
        String productType;
        boolean isMismatch;
    }
}
