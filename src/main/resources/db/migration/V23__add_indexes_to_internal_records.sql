-- Add additional indexes to internal_records table for optimized reconciliation queries
-- Note: Base indexes (rrn, rrn+stan, reference_number, terminal_id+transaction_date, transaction_date)
-- were already created in V16. This migration adds supplementary indexes.

-- Index for STAN-only lookups (rare, but included for completeness)
CREATE INDEX idx_internal_records_stan ON internal_records(stan);

-- Index for PAN lookups
CREATE INDEX idx_internal_records_pan ON internal_records(pan);

-- Index for processorReference lookups
CREATE INDEX idx_internal_records_processor_ref ON internal_records(processor_reference);
