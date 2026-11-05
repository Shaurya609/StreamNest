CREATE TABLE IF NOT EXISTS rooms (
  id TEXT PRIMARY KEY,
  revision INTEGER NOT NULL,
  payload TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS rooms_updated ON rooms(updated_at);
