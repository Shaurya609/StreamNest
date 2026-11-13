
CREATE TABLE IF NOT EXISTS diagnostic_devices (
 room_id TEXT NOT NULL, device_id TEXT NOT NULL, config TEXT NOT NULL, updated_at INTEGER NOT NULL,
 PRIMARY KEY(room_id,device_id)
);
CREATE TABLE IF NOT EXISTS diagnostic_events (
 room_id TEXT NOT NULL, device_id TEXT NOT NULL, event_id TEXT NOT NULL, session_id TEXT NOT NULL,
 event_type TEXT NOT NULL, level TEXT NOT NULL, event_at INTEGER NOT NULL, received_at INTEGER NOT NULL, payload TEXT NOT NULL,
 PRIMARY KEY(room_id,device_id,event_id)
);
CREATE INDEX IF NOT EXISTS diagnostic_event_device ON diagnostic_events(room_id,device_id,event_at DESC);
CREATE INDEX IF NOT EXISTS diagnostic_event_received ON diagnostic_events(received_at);
