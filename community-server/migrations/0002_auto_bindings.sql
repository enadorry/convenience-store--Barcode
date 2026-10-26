CREATE TABLE auto_bindings (
 id TEXT PRIMARY KEY,
 request_id TEXT NOT NULL UNIQUE,
 payload_hash TEXT NOT NULL,
 rate_bucket TEXT NOT NULL,
 jan TEXT NOT NULL CHECK(length(jan) IN (8,13)),
 name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 200),
 unit_cents INTEGER NOT NULL CHECK(typeof(unit_cents)='integer' AND unit_cents BETWEEN 1 AND 110000000),
 source_kind TEXT NOT NULL CHECK(source_kind IN ('manual','seven')),
 source_url TEXT NOT NULL,
 observed_on TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected')),
 created_at INTEGER NOT NULL,
 reviewed_at INTEGER
);
CREATE INDEX bindings_public ON auto_bindings(jan,status,observed_on DESC,created_at DESC);
CREATE INDEX bindings_rate ON auto_bindings(rate_bucket,created_at);
CREATE INDEX bindings_created ON auto_bindings(created_at);
