package org.settlementservice.settlementservice.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.exceptions.FileParseException;
import org.settlementservice.settlementservice.parsers.ParsedFile;
import org.settlementservice.settlementservice.parsers.ParsedRow;
import org.settlementservice.settlementservice.parsers.RowParseError;
import org.settlementservice.settlementservice.parsers.StatementFileParser;
import org.settlementservice.settlementservice.parsers.StatementFileParserFactory;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRowErrorRepository;
import org.settlementservice.settlementservice.repositories.ClassificationRuleRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankStatementUploadTaskTest {

    @Mock
    private BankStatementRepository bankStatementRepository;

    @Mock
    private BankStatementRowErrorRepository bankStatementRowErrorRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ClassificationRuleRepository classificationRuleRepository;

    @Mock
    private StatementFileParserFactory statementFileParserFactory;

    @Mock
    private StatementFileParser parser;

    @Mock
    private ClassificationMatcher classificationMatcher;

    private BankStatementUploadTask task;

    @BeforeEach
    void setUp() {
        task = new BankStatementUploadTask(
                bankStatementRepository, bankStatementRowErrorRepository, transactionRepository,
                classificationRuleRepository, statementFileParserFactory, classificationMatcher);
    }

    @Test
    void process_bankStatementNotFound_doesNothing() {
        when(bankStatementRepository.findById(99L)).thenReturn(Optional.empty());

        task.process(99L, "statement.xlsx", "file".getBytes());

        verifyNoInteractions(statementFileParserFactory, transactionRepository, bankStatementRowErrorRepository);
        verify(bankStatementRepository, never()).save(any());
    }

    @Test
    void process_wholeFileUnreadable_marksBankStatementFailedWithErrorMessage() {
        BankStatement bankStatement = bankStatement();
        when(bankStatementRepository.findById(1L)).thenReturn(Optional.of(bankStatement));
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statementFileParserFactory.getParser("statement.xlsx")).thenReturn(parser);
        when(parser.parseBankStatement(any())).thenThrow(new FileParseException("not a readable workbook"));

        task.process(1L, "statement.xlsx", "file".getBytes());

        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(bankStatement.getErrorMessage()).isEqualTo("not a readable workbook");
        verifyNoInteractions(transactionRepository, bankStatementRowErrorRepository);
    }

    @Test
    void process_allRowsSaveSuccessfully_marksCompletedAndAppliesClassification() {
        BankStatement bankStatement = bankStatement();
        when(bankStatementRepository.findById(1L)).thenReturn(Optional.of(bankStatement));
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classificationRuleRepository.findByAccountIdOrAccountIsNull(3L)).thenReturn(List.of());
        when(transactionRepository.findByAccountIdAndReferenceNumber(any(), any())).thenReturn(Optional.empty());
        when(statementFileParserFactory.getParser("statement.csv")).thenReturn(parser);

        ParsedRow row = transactionRow(2, "REF-001");
        when(parser.parseBankStatement(any())).thenReturn(
                new ParsedFile(List.of(row), List.of()));
        when(classificationMatcher.classify("CARD SETTLEMENT", List.of())).thenReturn(Optional.of(ProductType.CARD_SETTLEMENT));

        task.process(1L, "statement.csv", "file".getBytes());

        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(bankStatement.getTotalEntries()).isEqualTo(1);
        assertThat(bankStatement.getClosingBalance()).isEqualByComparingTo(new BigDecimal("105000"));
        verify(transactionRepository).saveAll(argThat((List<Transaction> saved) ->
                saved.size() == 1
                        && saved.get(0).getReferenceNumber().equals("REF-001")
                        && saved.get(0).getProductType() == ProductType.CARD_SETTLEMENT));
        verifyNoInteractions(bankStatementRowErrorRepository);
    }

    @Test
    void process_parseRowErrorsPresent_failsWholeBatchButStillRecordsRowErrors() {
        BankStatement bankStatement = bankStatement();
        when(bankStatementRepository.findById(1L)).thenReturn(Optional.of(bankStatement));
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statementFileParserFactory.getParser("statement.xlsx")).thenReturn(parser);

        ParsedRow goodRow = transactionRow(3, "REF-002");
        RowParseError parseError = new RowParseError(2, "raw bad row", "Debit has an unparseable numeric value");
        when(parser.parseBankStatement(any())).thenReturn(
                new ParsedFile(List.of(goodRow), List.of(parseError)));

        task.process(1L, "statement.xlsx", "file".getBytes());

        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(bankStatement.getTotalEntries()).isEqualTo(2);
        assertThat(bankStatement.getErrorMessage()).contains("1 row(s) failed to parse");
        assertThat(bankStatement.getClosingBalance()).isNull();
        verify(bankStatementRowErrorRepository, times(1)).save(any());
        verifyNoInteractions(transactionRepository, classificationRuleRepository);
    }

    @Test
    void process_referenceNumberAlreadyImported_skipsRowWithoutTreatingItAsAnError() {
        BankStatement bankStatement = bankStatement();
        when(bankStatementRepository.findById(1L)).thenReturn(Optional.of(bankStatement));
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classificationRuleRepository.findByAccountIdOrAccountIsNull(3L)).thenReturn(List.of());
        when(transactionRepository.findByAccountIdAndReferenceNumber(3L, "REF-001"))
                .thenReturn(Optional.of(new Transaction()));
        when(statementFileParserFactory.getParser("statement.xlsx")).thenReturn(parser);

        ParsedRow row = transactionRow(2, "REF-001");
        when(parser.parseBankStatement(any())).thenReturn(
                new ParsedFile(List.of(row), List.of()));

        task.process(1L, "statement.xlsx", "file".getBytes());

        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(transactionRepository).saveAll(argThat((List<Transaction> saved) -> saved.isEmpty()));
        verifyNoInteractions(bankStatementRowErrorRepository);
    }

    @Test
    void process_saveAllThrowsUnexpectedException_failsWholeBatch() {
        BankStatement bankStatement = bankStatement();
        when(bankStatementRepository.findById(1L)).thenReturn(Optional.of(bankStatement));
        when(bankStatementRepository.save(any(BankStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classificationRuleRepository.findByAccountIdOrAccountIsNull(3L)).thenReturn(List.of());
        when(transactionRepository.findByAccountIdAndReferenceNumber(any(), any())).thenReturn(Optional.empty());
        when(classificationMatcher.classify(any(), any())).thenReturn(Optional.empty());
        when(transactionRepository.saveAll(any())).thenThrow(new RuntimeException("constraint violation"));
        when(statementFileParserFactory.getParser("statement.xlsx")).thenReturn(parser);

        ParsedRow row = transactionRow(2, "REF-001");
        when(parser.parseBankStatement(any())).thenReturn(
                new ParsedFile(List.of(row), List.of()));

        task.process(1L, "statement.xlsx", "file".getBytes());

        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(bankStatement.getErrorMessage()).isEqualTo("constraint violation");
    }

    private BankStatement bankStatement() {
        BankStatement bankStatement = new BankStatement();
        bankStatement.setId(1L);
        bankStatement.setOpeningBalance(new BigDecimal("100000"));
        Account account = new Account();
        account.setId(3L);
        bankStatement.setAccount(account);
        return bankStatement;
    }

    private ParsedRow transactionRow(int rowNumber, String referenceNumber) {
        return new ParsedRow(rowNumber, LocalDate.of(2026, 6, 2), null, "CARD SETTLEMENT",
                referenceNumber, BigDecimal.ZERO, new BigDecimal("5000"), null, null, null, null, null);
    }
}
