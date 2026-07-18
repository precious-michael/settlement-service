package org.settlementservice.settlementservice.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.exceptions.FileParseException;
import org.settlementservice.settlementservice.parsers.ParsedFile;
import org.settlementservice.settlementservice.parsers.ParsedRow;
import org.settlementservice.settlementservice.parsers.RowParseError;
import org.settlementservice.settlementservice.parsers.StatementFileParser;
import org.settlementservice.settlementservice.parsers.StatementFileParserFactory;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRowErrorRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;

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
class SettlementReportUploadTaskTest {

    @Mock
    private SettlementReportRepository settlementReportRepository;

    @Mock
    private SettlementReportRowErrorRepository settlementReportRowErrorRepository;

    @Mock
    private SettlementTransactionRepository settlementTransactionRepository;

    @Mock
    private StatementFileParserFactory statementFileParserFactory;

    @Mock
    private StatementFileParser parser;

    private SettlementReportUploadTask task;

    @BeforeEach
    void setUp() {
        task = new SettlementReportUploadTask(
                settlementReportRepository, settlementReportRowErrorRepository, settlementTransactionRepository,
                statementFileParserFactory);
    }

    @Test
    void process_settlementReportNotFound_doesNothing() {
        when(settlementReportRepository.findById(99L)).thenReturn(Optional.empty());

        task.process(99L, "report.csv", "file".getBytes());

        verifyNoInteractions(statementFileParserFactory, settlementTransactionRepository, settlementReportRowErrorRepository);
        verify(settlementReportRepository, never()).save(any());
    }

    @Test
    void process_wholeFileUnreadable_marksSettlementReportFailedWithErrorMessage() {
        SettlementReport settlementReport = settlementReport();
        when(settlementReportRepository.findById(1L)).thenReturn(Optional.of(settlementReport));
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statementFileParserFactory.getParser("report.csv")).thenReturn(parser);
        when(parser.parseSettlementReport(any())).thenThrow(new FileParseException("not a readable CSV"));

        task.process(1L, "report.csv", "file".getBytes());

        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(settlementReport.getErrorMessage()).isEqualTo("not a readable CSV");
        verifyNoInteractions(settlementTransactionRepository, settlementReportRowErrorRepository);
    }

    @Test
    void process_allRowsSaveSuccessfully_marksCompleted() {
        SettlementReport settlementReport = settlementReport();
        when(settlementReportRepository.findById(1L)).thenReturn(Optional.of(settlementReport));
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statementFileParserFactory.getParser("report.xlsx")).thenReturn(parser);

        ParsedRow row = settlementRow(2, "REF-001");
        when(parser.parseSettlementReport(any())).thenReturn(new ParsedFile(List.of(row), List.of()));

        task.process(1L, "report.xlsx", "file".getBytes());

        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementReport.getTotalEntries()).isEqualTo(1);
        verify(settlementTransactionRepository).saveAll(argThat((List<SettlementTransaction> saved) ->
                saved.size() == 1 && saved.get(0).getTransactionReference().equals("REF-001")));
        verifyNoInteractions(settlementReportRowErrorRepository);
    }

    @Test
    void process_parseRowErrorsPresent_marksCompletedWithErrorsAndRecordsThem() {
        SettlementReport settlementReport = settlementReport();
        when(settlementReportRepository.findById(1L)).thenReturn(Optional.of(settlementReport));
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statementFileParserFactory.getParser("report.csv")).thenReturn(parser);

        ParsedRow goodRow = settlementRow(3, "REF-002");
        RowParseError parseError = new RowParseError(2, "raw bad row", "narration is required");
        when(parser.parseSettlementReport(any())).thenReturn(new ParsedFile(List.of(goodRow), List.of(parseError)));

        task.process(1L, "report.csv", "file".getBytes());

        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.COMPLETED_WITH_ERRORS);
        assertThat(settlementReport.getTotalEntries()).isEqualTo(2);
        verify(settlementReportRowErrorRepository, times(1)).save(any());
        verify(settlementTransactionRepository).saveAll(argThat((List<SettlementTransaction> saved) -> saved.size() == 1));
    }

    @Test
    void process_saveAllThrowsUnexpectedException_failsWholeBatch() {
        SettlementReport settlementReport = settlementReport();
        when(settlementReportRepository.findById(1L)).thenReturn(Optional.of(settlementReport));
        when(settlementReportRepository.save(any(SettlementReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementTransactionRepository.saveAll(any())).thenThrow(new RuntimeException("constraint violation"));
        when(statementFileParserFactory.getParser("report.csv")).thenReturn(parser);

        ParsedRow row = settlementRow(2, "REF-001");
        when(parser.parseSettlementReport(any())).thenReturn(new ParsedFile(List.of(row), List.of()));

        task.process(1L, "report.csv", "file".getBytes());

        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(settlementReport.getErrorMessage()).isEqualTo("constraint violation");
    }

    private SettlementReport settlementReport() {
        SettlementReport settlementReport = new SettlementReport();
        settlementReport.setId(1L);
        return settlementReport;
    }

    private ParsedRow settlementRow(int rowNumber, String reference) {
        return new ParsedRow(rowNumber, LocalDate.of(2026, 6, 2), null, "CARD SETTLEMENT", reference,
                BigDecimal.ZERO, new BigDecimal("5000"), null);
    }
}
