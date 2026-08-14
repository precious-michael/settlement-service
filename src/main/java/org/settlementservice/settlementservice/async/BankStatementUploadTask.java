package org.settlementservice.settlementservice.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.models.BankStatementRowError;
import org.settlementservice.settlementservice.models.ClassificationRule;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.parsers.ParsedFile;
import org.settlementservice.settlementservice.parsers.ParsedRow;
import org.settlementservice.settlementservice.parsers.RowParseError;
import org.settlementservice.settlementservice.parsers.StatementFileParserFactory;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRowErrorRepository;
import org.settlementservice.settlementservice.repositories.ClassificationRuleRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an uploaded bank statement and persists its rows in the background, off the request
 * thread. Triggered directly from {@code BankStatementServiceImpl} once the upload's own
 * transaction has already committed — not from inside it — so processing never starts before the
 * batch row it depends on is visible. Rows with an already-imported reference number are skipped
 * (not an error). Any row the parser couldn't read fails the whole batch outright — row errors are
 * still recorded individually for visibility, but no transactions are persisted and no closing
 * balance is computed, since a closing balance computed from an incomplete row set would be wrong
 * and nothing downstream can tell it apart from a correct one. Fix the file and re-upload.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankStatementUploadTask {

    private final BankStatementRepository bankStatementRepository;
    private final BankStatementRowErrorRepository bankStatementRowErrorRepository;
    private final TransactionRepository transactionRepository;
    private final ClassificationRuleRepository classificationRuleRepository;
    private final AccountRepository accountRepository;
    private final StatementFileParserFactory statementFileParserFactory;
    private final ClassificationMatcher classificationMatcher;

    @Async("file-processing")
    public void process(Long bankStatementId, String fileName, byte[] fileBytes) {
        BankStatement bankStatement = bankStatementRepository.findById(bankStatementId).orElse(null);
        if (bankStatement == null) {
            log.warn("Bank statement {} not found when starting async processing", bankStatementId);
            return;
        }

        bankStatement.setStatus(BatchStatus.PROCESSING);
        bankStatement = bankStatementRepository.save(bankStatement);

        try {
            processFile(bankStatement, fileName, fileBytes);
        } catch (Exception e) {
            log.error("Failed to process bank statement {}", bankStatementId, e);
            bankStatement.setStatus(BatchStatus.FAILED);
            bankStatement.setErrorMessage(e.getMessage());
            bankStatementRepository.save(bankStatement);
        }
    }

    private void processFile(BankStatement bankStatement, String fileName, byte[] fileBytes) {
        ParsedFile parsed = statementFileParserFactory.getParser(fileName).parseBankStatement(fileBytes);
        int totalEntries = parsed.getRows().size() + parsed.getRowErrors().size();

        for (RowParseError error : parsed.getRowErrors()) {
            saveRowError(bankStatement, error.getRowNumber(), error.getRawRow(), error.getMessage());
        }

        if (!parsed.getRowErrors().isEmpty()) {
            bankStatement.setTotalEntries(totalEntries);
            bankStatement.setStatus(BatchStatus.FAILED);
            bankStatement.setErrorMessage(parsed.getRowErrors().size()
                    + " row(s) failed to parse — upload rejected rather than computing a closing balance "
                    + "from an incomplete row set. Fix the file and re-upload.");
            bankStatementRepository.save(bankStatement);
            return;
        }

        Long accountId = bankStatement.getAccount().getId();
        List<ClassificationRule> rules = classificationRuleRepository.findByAccountIdOrAccountIsNull(accountId);

        List<Transaction> transactions = new ArrayList<>();
        for (ParsedRow row : parsed.getRows()) {
            boolean alreadyImported = row.getReferenceNumber() != null
                    && transactionRepository.findByAccountIdAndReferenceNumber(accountId, row.getReferenceNumber())
                            .isPresent();
            if (alreadyImported) {
                continue;
            }
            transactions.add(toTransaction(bankStatement, row, rules));
        }
        transactionRepository.saveAll(transactions);

        BigDecimal closingBalance = computeClosingBalance(bankStatement.getOpeningBalance(), parsed.getRows());
        LocalDate closingDate = computeClosingDate(parsed.getRows());

        bankStatement.setTotalEntries(totalEntries);
        bankStatement.setClosingBalance(closingBalance);
        bankStatement.setClosingDate(closingDate);
        bankStatement.setStatus(BatchStatus.COMPLETED);
        bankStatementRepository.save(bankStatement);

        // Update account's opening balance to this statement's closing balance
        // Fetch account by ID to avoid LazyInitializationException
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setOpeningBalance(closingBalance);
            accountRepository.save(account);
            log.info("Updated account {} opening balance to {} (from statement {})",
                    accountId, closingBalance, bankStatement.getId());
        });
    }

    private BigDecimal computeClosingBalance(BigDecimal openingBalance, List<ParsedRow> rows) {
        BigDecimal closingBalance = openingBalance != null ? openingBalance : BigDecimal.ZERO;
        for (ParsedRow row : rows) {
            closingBalance = closingBalance.add(row.getCredit()).subtract(row.getDebit());
        }
        return closingBalance;
    }

    /**
     * Computes the closing date as the latest transaction date in the statement.
     * This represents the end of the statement period for continuity checking.
     */
    private LocalDate computeClosingDate(List<ParsedRow> rows) {
        return rows.stream()
                .map(ParsedRow::getTransactionDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private Transaction toTransaction(BankStatement bankStatement, ParsedRow row, List<ClassificationRule> rules) {
        Transaction transaction = new Transaction();
        transaction.setBankStatement(bankStatement);
        transaction.setAccount(bankStatement.getAccount());
        transaction.setTransactionDate(row.getTransactionDate());
        transaction.setValueDate(row.getValueDate());
        transaction.setNarration(row.getNarration());
        transaction.setReferenceNumber(row.getReferenceNumber());
        transaction.setDebit(row.getDebit());
        transaction.setCredit(row.getCredit());
        transaction.setBalance(row.getBalance());
        transaction.setProductType(classificationMatcher.classify(row.getNarration(), rules).orElse(ProductType.OTHERS));
        transaction.setStatus(TransactionStatus.UNRESOLVED);
        return transaction;
    }

    private void saveRowError(BankStatement bankStatement, int rowNumber, String rawRow, String message) {
        BankStatementRowError rowError = new BankStatementRowError();
        rowError.setBankStatement(bankStatement);
        rowError.setRowNumber(rowNumber);
        rowError.setRawRow(rawRow);
        rowError.setErrorMessage(message);
        bankStatementRowErrorRepository.save(rowError);
    }
}
