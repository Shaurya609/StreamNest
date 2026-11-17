
CREATE TABLE IF NOT EXISTS revoked_groups (id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS watch_activity (
 room_id TEXT NOT NULL, device_id TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL,
 PRIMARY KEY(room_id,device_id)
);
CREATE INDEX IF NOT EXISTS watch_activity_updated ON watch_activity(updated_at);
