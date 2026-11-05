CREATE TABLE IF NOT EXISTS rooms (
  id TEXT PRIMARY KEY,
  revision INTEGER NOT NULL,
  payload TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS rooms_updated ON rooms(updated_at);

CREATE TABLE IF NOT EXISTS pairing_tickets (id TEXT PRIMARY KEY, payload TEXT NOT NULL, expires_at INTEGER NOT NULL);
