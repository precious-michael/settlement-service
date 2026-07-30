package org.settlementservice.settlementservice.controllers;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.controllers.config.SynchronousAsyncTestConfig;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.enums.BatchStatus;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRowErrorRepository;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: upload a real .xlsx through the controller and verify the pipeline actually
 * classifies and persists Transaction rows. Imports {@link SynchronousAsyncTestConfig} so the
 * controller's direct call into {@code BankStatementProcessor} runs inline instead of on a real
 * background thread — since it's triggered directly (no event, no commit-gating), it naturally
 * runs inside this test's normal rollback-wrapped transaction, so no special opt-out or manual
 * cleanup is needed (unlike the async design this replaced).
 */
@Import(SynchronousAsyncTestConfig.class)
class BankStatementUploadProcessingIntegrationTest extends AbstractControllerTest {

    @Autowired
    private SettlementBankRepository settlementBankRepository;

    @Autowired
    private BankStatementRepository bankStatementRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BankStatementRowErrorRepository bankStatementRowErrorRepository;

    @Test
    void upload_realWorkbook_classifiesAndPersistsTransactionsAndMarksCompleted() throws Exception {
        Long accountId = createAccount("1234567890");
        byte[] workbook = workbook(sheet -> {
            writeTableHeader(sheet, 0);
            writeDataRow(sheet, 1, "2026-06-02", "2026-06-02", "CARD SETTLEMENT FEE", "REF-101", "0", "5000", "155000");
            writeDataRow(sheet, 2, "2026-06-03", "2026-06-03", "PAYROLL RUN", "REF-102", "0", "20000", "175000");
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        String response = mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bankStatementId = objectMapper.readTree(response).get("data").get("id").asLong();

        BankStatement bankStatement = bankStatementRepository.findById(bankStatementId).orElseThrow();
        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(bankStatement.getTotalEntries()).isEqualTo(2);
        assertThat(bankStatement.getClosingBalance()).isEqualByComparingTo("125000");

        List<Transaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getAccount().getId().equals(accountId))
                .toList();
        assertThat(transactions).hasSize(2);
        assertThat(transactions).anySatisfy(t -> {
            assertThat(t.getReferenceNumber()).isEqualTo("REF-101");
            assertThat(t.getProductType()).isEqualTo(ProductType.CARD_SETTLEMENT);
        });
        assertThat(transactions).anySatisfy(t -> {
            assertThat(t.getReferenceNumber()).isEqualTo("REF-102");
            assertThat(t.getProductType()).isEqualTo(ProductType.PAYROLL);
        });
    }

    @Test
    void upload_workbookWithOneBadRow_failsWholeBatchButStillRecordsRowError() throws Exception {
        Long accountId = createAccount("1234567891");
        byte[] workbook = workbook(sheet -> {
            writeTableHeader(sheet, 0);
            writeDataRow(sheet, 1, "2026-06-02", "2026-06-02", "CARD SETTLEMENT FEE", "REF-201", "not-a-number", "5000", "155000");
            writeDataRow(sheet, 2, "2026-06-03", "2026-06-03", "PAYROLL RUN", "REF-202", "0", "20000", "175000");
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        String response = mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bankStatementId = objectMapper.readTree(response).get("data").get("id").asLong();

        BankStatement bankStatement = bankStatementRepository.findById(bankStatementId).orElseThrow();
        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(bankStatement.getClosingBalance()).isNull();

        List<Transaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getAccount().getId().equals(accountId))
                .toList();
        assertThat(transactions).isEmpty();

        assertThat(bankStatementRowErrorRepository.findAll().stream()
                .filter(e -> e.getBankStatement().getId().equals(bankStatementId))
                .toList()).hasSize(1);
    }

    @Test
    void upload_realCsv_classifiesAndPersistsTransactionsAndMarksCompleted() throws Exception {
        Long accountId = createAccount("1234567892");
        String csv = """
                Transaction Date,Value Date,Narration,Reference Number,Debit,Credit,Balance
                2026-06-02,2026-06-02,CARD SETTLEMENT FEE,REF-301,0,5000,155000
                2026-06-03,2026-06-03,PAYROLL RUN,REF-302,0,20000,175000
                """;
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bankStatementId = objectMapper.readTree(response).get("data").get("id").asLong();

        BankStatement bankStatement = bankStatementRepository.findById(bankStatementId).orElseThrow();
        assertThat(bankStatement.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Transaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getAccount().getId().equals(accountId))
                .toList();
        assertThat(transactions).hasSize(2);
        assertThat(transactions).anySatisfy(t -> {
            assertThat(t.getReferenceNumber()).isEqualTo("REF-301");
            assertThat(t.getProductType()).isEqualTo(ProductType.CARD_SETTLEMENT);
        });
    }

    private Long createAccount(String accountNumber) throws Exception {
        SettlementBank gtBank = settlementBankRepository.findByCode("058").orElseThrow();

        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setName("Processing Test Co");
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

    private void writeTableHeader(Sheet sheet, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        String[] columns = {"Transaction Date", "Value Date", "Narration", "Reference Number", "Debit", "Credit", "Balance"};
        for (int i = 0; i < columns.length; i++) {
            row.createCell(i).setCellValue(columns[i]);
        }
    }

    private void writeDataRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
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
