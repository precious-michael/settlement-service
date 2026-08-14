-- Add detailed debit/credit breakdown and match information to discrepancies
-- This makes discrepancies more readable by showing separate debit/credit amounts
-- instead of signed net amounts

ALTER TABLE discrepancies
ADD COLUMN expected_debit DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER expected_amount,
ADD COLUMN expected_credit DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER expected_debit,
ADD COLUMN reported_debit DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER reported_amount,
ADD COLUMN reported_credit DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER reported_debit,
ADD COLUMN matched_on VARCHAR(500) NULL AFTER difference;

-- Backfill existing records: convert net amounts to debit/credit
-- If expected_amount is negative, it's a debit; if positive, it's a credit
UPDATE discrepancies
SET
    expected_debit = CASE WHEN expected_amount < 0 THEN ABS(expected_amount) ELSE 0 END,
    expected_credit = CASE WHEN expected_amount > 0 THEN expected_amount ELSE 0 END,
    reported_debit = CASE WHEN reported_amount < 0 THEN ABS(reported_amount) ELSE 0 END,
    reported_credit = CASE WHEN reported_amount > 0 THEN reported_amount ELSE 0 END,
    difference = ABS(difference);
