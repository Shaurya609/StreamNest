CREATE TABLE IF NOT EXISTS rooms (
  id TEXT PRIMARY KEY,
  revision INTEGER NOT NULL,
  payload TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS rooms_updated ON rooms(updated_at);

CREATE TABLE IF NOT EXISTS pairing_tickets (id TEXT PRIMARY KEY, payload TEXT NOT NULL, expires_at INTEGER NOT NULL);


CREATE TABLE IF NOT EXISTS sync_groups (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sync_devices (
 room_id TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, seen_at INTEGER NOT NULL,
 PRIMARY KEY(room_id,id)
);
CREATE INDEX IF NOT EXISTS sync_devices_seen ON sync_devices(seen_at);
