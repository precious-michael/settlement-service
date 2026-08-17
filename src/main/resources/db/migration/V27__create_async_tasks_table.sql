CREATE TABLE async_tasks (
     id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
     type VARCHAR(50) NOT NULL,
     status VARCHAR(50) NOT NULL,
     total_records BIGINT NOT NULL DEFAULT 0,
     processed_records BIGINT NOT NULL DEFAULT 0,
     error_message VARCHAR(1000),
     started_at DATETIME NOT NULL,
     completed_at DATETIME,
     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     INDEX idx_type_status (type, status),
     INDEX idx_started_at (started_at)
);
