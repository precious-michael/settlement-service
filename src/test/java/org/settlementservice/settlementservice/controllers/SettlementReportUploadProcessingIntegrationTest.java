package org.settlementservice.settlementservice.controllers;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.controllers.config.SynchronousAsyncTestConfig;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.models.SettlementReport;
import org.settlementservice.settlementservice.models.SettlementTransaction;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRepository;
import org.settlementservice.settlementservice.repositories.SettlementReportRowErrorRepository;
import org.settlementservice.settlementservice.repositories.SettlementTransactionRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: upload a real settlement report CSV through the controller and verify the pipeline
 * persists SettlementTransaction rows. Same setup as
 * {@link BankStatementUploadProcessingIntegrationTest} — see its class comment for why no
 * transaction opt-out or manual cleanup is needed here either.
 */
@Import(SynchronousAsyncTestConfig.class)
class SettlementReportUploadProcessingIntegrationTest extends AbstractControllerTest {

    @Autowired
    private SettlementBankRepository settlementBankRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SettlementReportRepository settlementReportRepository;

    @Autowired
    private SettlementTransactionRepository settlementTransactionRepository;

    @Autowired
    private SettlementReportRowErrorRepository settlementReportRowErrorRepository;

    @Test
    void upload_realCsv_persistsSettlementTransactionsAndMarksCompleted() throws Exception {
        Long accountId = createAccount("2234567890");
        Long transactionId = createTransactionViaStatementUpload(accountId, "REF-301");

        String csv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,CARD SETTLEMENT FEE,REF-301,0,2500
                2026-06-02,CARD SETTLEMENT FEE SPLIT,REF-301,0,2500
                """;
        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long settlementReportId = objectMapper.readTree(response).get("data").get("id").asLong();

        SettlementReport settlementReport = settlementReportRepository.findById(settlementReportId).orElseThrow();
        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(settlementReport.getTotalEntries()).isEqualTo(2);

        List<SettlementTransaction> rows = settlementTransactionRepository.findBySettlementReportId(settlementReportId);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.getTransactionReference()).isEqualTo("REF-301"));
    }

    @Test
    void upload_csvWithOneBadRow_marksCompletedWithErrorsAndRecordsRowError() throws Exception {
        Long accountId = createAccount("2234567891");
        Long transactionId = createTransactionViaStatementUpload(accountId, "REF-401");

        String csv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,CARD SETTLEMENT FEE,REF-401,0,not-a-number
                2026-06-02,CARD SETTLEMENT FEE SPLIT,REF-401,0,2500
                """;
        MockMultipartFile file = new MockMultipartFile("file", "report.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long settlementReportId = objectMapper.readTree(response).get("data").get("id").asLong();

        SettlementReport settlementReport = settlementReportRepository.findById(settlementReportId).orElseThrow();
        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.COMPLETED_WITH_ERRORS);

        assertThat(settlementTransactionRepository.findBySettlementReportId(settlementReportId)).hasSize(1);
        assertThat(settlementReportRowErrorRepository.findAll().stream()
                .filter(e -> e.getSettlementReport().getId().equals(settlementReportId))
                .toList()).hasSize(1);
    }

    @Test
    void upload_reportedAmountOutsideTolerance_rejectsReportAndAllowsCorrectedRetry() throws Exception {
        Long accountId = createAccount("2234567893");
        Long transactionId = createTransactionViaStatementUpload(accountId, "REF-601");

        String mismatchedCsv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,CARD SETTLEMENT FEE,REF-601,0,4800
                """;
        MockMultipartFile mismatchedFile = new MockMultipartFile("file", "report.csv", "text/csv",
                mismatchedCsv.getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(mismatchedFile)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rejectedReportId = objectMapper.readTree(response).get("data").get("id").asLong();

        assertThat(settlementReportRepository.findById(rejectedReportId)).isEmpty();
        assertThat(settlementTransactionRepository.findBySettlementReportId(rejectedReportId)).isEmpty();
        assertThat(transactionRepository.findById(transactionId).orElseThrow().getStatus())
                .isNotEqualTo(TransactionStatus.MISMATCHED);

        // The rejected report's row was deleted, so its unique FK on transactionId no longer
        // blocks a corrected re-upload for the same transaction.
        String correctedCsv = """
                transaction_date,narration,transaction_reference,debit,credit
                2026-06-02,CARD SETTLEMENT FEE,REF-601,0,5000
                """;
        MockMultipartFile correctedFile = new MockMultipartFile("file", "report.csv", "text/csv",
                correctedCsv.getBytes(StandardCharsets.UTF_8));

        String retryResponse = mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(correctedFile)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long acceptedReportId = objectMapper.readTree(retryResponse).get("data").get("id").asLong();

        SettlementReport acceptedReport = settlementReportRepository.findById(acceptedReportId).orElseThrow();
        assertThat(acceptedReport.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void upload_realXlsx_persistsSettlementTransactionsAndMarksCompleted() throws Exception {
        Long accountId = createAccount("2234567892");
        Long transactionId = createTransactionViaStatementUpload(accountId, "REF-501");

        byte[] reportWorkbook = workbook(sheet -> {
            Row header = sheet.createRow(0);
            String[] columns = {"Transaction Date", "Narration", "Transaction Reference", "Debit", "Credit"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("2026-06-02");
            data.createCell(1).setCellValue("CARD SETTLEMENT FEE");
            data.createCell(2).setCellValue("REF-501");
            data.createCell(3).setCellValue("0");
            data.createCell(4).setCellValue("5000");
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", reportWorkbook);

        String response = mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long settlementReportId = objectMapper.readTree(response).get("data").get("id").asLong();

        SettlementReport settlementReport = settlementReportRepository.findById(settlementReportId).orElseThrow();
        assertThat(settlementReport.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<SettlementTransaction> rows = settlementTransactionRepository.findBySettlementReportId(settlementReportId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTransactionReference()).isEqualTo("REF-501");
    }

    private Long createAccount(String accountNumber) throws Exception {
        SettlementBank gtBank = settlementBankRepository.findByCode("058").orElseThrow();

        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setName("Report Processing Test Co");
        accountRequest.setAccountNumber(accountNumber);
        accountRequest.setBankId(gtBank.getId());

        String response = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private Long createTransactionViaStatementUpload(Long accountId, String referenceNumber) throws Exception {
        byte[] workbook = workbook(sheet -> {
            Row header = sheet.createRow(0);
            String[] columns = {"Transaction Date", "Value Date", "Narration", "Reference Number", "Debit", "Credit", "Balance"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("2026-06-01");
            data.createCell(1).setCellValue("2026-06-01");
            data.createCell(2).setCellValue("CARD SETTLEMENT FEE");
            data.createCell(3).setCellValue(referenceNumber);
            data.createCell(4).setCellValue("0");
            data.createCell(5).setCellValue("5000");
            data.createCell(6).setCellValue("155000");
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated());

        return transactionRepository.findByAccountIdAndReferenceNumber(accountId, referenceNumber).orElseThrow().getId();
    }

    private byte[] workbook(Consumer<Sheet> sheetBuilder) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Statement");
            sheetBuilder.accept(sheet);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
