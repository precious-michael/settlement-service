-- Add closing_date column to bank_statements table
-- This represents the end date of the statement period for continuity between consecutive statements

ALTER TABLE bank_statements
ADD COLUMN closing_date DATE NULL;
