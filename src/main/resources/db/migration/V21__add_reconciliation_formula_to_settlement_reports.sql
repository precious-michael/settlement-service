-- Add reconciliation_formula_id to settlement_reports
-- Links each settlement report to the formula used for reconciliation matching

ALTER TABLE settlement_reports
    ADD COLUMN reconciliation_formula_id BIGINT NULL,
    ADD CONSTRAINT fk_settlement_reports_reconciliation_formula
        FOREIGN KEY (reconciliation_formula_id) REFERENCES reconciliation_formulas (id);

CREATE INDEX idx_settlement_reports_formula ON settlement_reports (reconciliation_formula_id);
