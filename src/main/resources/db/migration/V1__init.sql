CREATE TABLE drill_run (
  id VARCHAR(36) PRIMARY KEY,
  target_id VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  status VARCHAR(32) NOT NULL,
  backup_ms BIGINT,
  backup_bytes BIGINT,
  container_start_ms BIGINT,
  restore_ms BIGINT,
  verify_ms BIGINT,
  rto_ms BIGINT,
  artifact_sha256 VARCHAR(64),
  checks_json CLOB,
  error CLOB
);
CREATE INDEX idx_drill_target_time ON drill_run (target_id, started_at DESC);
