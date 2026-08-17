ALTER TABLE async_tasks ADD COLUMN matched_count BIGINT;
ALTER TABLE async_tasks ADD COLUMN unmatched_count BIGINT;
ALTER TABLE async_tasks ADD COLUMN missing_count BIGINT;
