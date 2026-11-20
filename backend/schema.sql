CREATE TABLE IF NOT EXISTS operations (
  operation_id TEXT PRIMARY KEY,
  state TEXT NOT NULL CHECK (state IN ('sending','delivered','unknown','failed')),
  payload_sha256 TEXT NOT NULL,
  created_at TEXT NOT NULL,
  delivered_at TEXT,
  telegram_message_id TEXT
);
