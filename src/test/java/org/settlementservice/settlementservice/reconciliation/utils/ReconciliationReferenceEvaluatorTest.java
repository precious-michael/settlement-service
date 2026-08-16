package org.settlementservice.settlementservice.reconciliation.utils;

import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.models.InternalRecord;
import org.settlementservice.settlementservice.models.SettlementTransaction;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationReferenceEvaluatorTest {

    @Test
    void evaluate_withMap_replacesPlaceholders() {
        Map<String, String> fields = new HashMap<>();
        fields.put("rrn", "123456");
        fields.put("stan", "789");

        String result = ReconciliationReferenceEvaluator.evaluate("${rrn}/${stan}", fields);

        assertThat(result).isEqualTo("123456/789");
    }

    @Test
    void evaluate_withMap_missingField_returnsNull() {
        Map<String, String> fields = new HashMap<>();
        fields.put("rrn", "123456");

        String result = ReconciliationReferenceEvaluator.evaluate("${rrn}/${stan}", fields);

        assertThat(result).isNull();
    }

    @Test
    void evaluate_withSettlementTransaction_usingTransactionReference_works() {
        SettlementTransaction st = new SettlementTransaction();
        st.setTransactionReference("REF123");

        String result = ReconciliationReferenceEvaluator.evaluate("${transactionReference}", st);

        assertThat(result).isEqualTo("REF123");
    }

    @Test
    void evaluate_withSettlementTransaction_usingReferenceNumber_works() {
        SettlementTransaction st = new SettlementTransaction();
        st.setTransactionReference("REF123");

        // referenceNumber is an alias for transactionReference
        String result = ReconciliationReferenceEvaluator.evaluate("${referenceNumber}", st);

        assertThat(result).isEqualTo("REF123");
    }

    @Test
    void evaluate_withSettlementTransaction_combinedFormula() {
        SettlementTransaction st = new SettlementTransaction();
        st.setRrn("RRN456");
        st.setStan("789");
        st.setTransactionReference("REF123");

        String result = ReconciliationReferenceEvaluator.evaluate("${rrn}/${stan}", st);

        assertThat(result).isEqualTo("RRN456/789");
    }

    @Test
    void evaluate_withSettlementTransaction_missingField_returnsNull() {
        SettlementTransaction st = new SettlementTransaction();
        st.setRrn("RRN456");

        String result = ReconciliationReferenceEvaluator.evaluate("${rrn}/${stan}", st);

        assertThat(result).isNull();
    }

    @Test
    void evaluate_withInternalRecord_usingReferenceNumber() {
        InternalRecord record = new InternalRecord();
        record.setReferenceNumber("INTERNAL123");

        String result = ReconciliationReferenceEvaluator.evaluate("${referenceNumber}", record);

        assertThat(result).isEqualTo("INTERNAL123");
    }

    @Test
    void evaluate_withInternalRecord_combinedFormula() {
        InternalRecord record = new InternalRecord();
        record.setRrn("RRN999");
        record.setStan("111");

        String result = ReconciliationReferenceEvaluator.evaluate("${rrn}/${stan}", record);

        assertThat(result).isEqualTo("RRN999/111");
    }

    @Test
    void evaluate_nullFormula_returnsNull() {
        SettlementTransaction st = new SettlementTransaction();
        st.setTransactionReference("REF123");

        String result = ReconciliationReferenceEvaluator.evaluate(null, st);

        assertThat(result).isNull();
    }

    @Test
    void evaluate_emptyFormula_returnsNull() {
        SettlementTransaction st = new SettlementTransaction();
        st.setTransactionReference("REF123");

        String result = ReconciliationReferenceEvaluator.evaluate("", st);

        assertThat(result).isNull();
    }

    @Test
    void evaluate_nullTransaction_returnsNull() {
        String result = ReconciliationReferenceEvaluator.evaluate("${referenceNumber}", (SettlementTransaction) null);

        assertThat(result).isNull();
    }

    @Test
    void evaluate_formulaWithoutPlaceholders_returnsAsIs() {
        SettlementTransaction st = new SettlementTransaction();

        String result = ReconciliationReferenceEvaluator.evaluate("CONSTANT_VALUE", st);

        assertThat(result).isEqualTo("CONSTANT_VALUE");
    }

    @Test
    void evaluate_withTransactionDate() {
        SettlementTransaction st = new SettlementTransaction();
        st.setTransactionDate(LocalDate.of(2026, 8, 15));

        String result = ReconciliationReferenceEvaluator.evaluate("${transactionDate}", st);

        assertThat(result).isEqualTo("2026-08-15");
    }
}
