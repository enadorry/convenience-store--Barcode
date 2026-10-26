CREATE TABLE contributors (
 id TEXT PRIMARY KEY,
 label TEXT NOT NULL CHECK(length(label) BETWEEN 1 AND 80),
 token_hash TEXT NOT NULL UNIQUE,
 revoked INTEGER NOT NULL DEFAULT 0 CHECK(revoked IN (0,1)),
 created_at INTEGER NOT NULL
);
CREATE TABLE products (
 jan TEXT PRIMARY KEY CHECK(length(jan) IN (8,13)),
 name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 200),
 created_at INTEGER NOT NULL
);
CREATE TABLE price_reports (
 id TEXT PRIMARY KEY,
 request_id TEXT NOT NULL UNIQUE,
 payload_hash TEXT NOT NULL,
 contributor_id TEXT NOT NULL REFERENCES contributors(id),
 jan TEXT NOT NULL CHECK(length(jan) IN (8,13)),
 name TEXT NOT NULL CHECK(length(name) BETWEEN 1 AND 200),
 unit_cents INTEGER NOT NULL CHECK(typeof(unit_cents)='integer' AND unit_cents BETWEEN 1 AND 110000000),
 store_name TEXT NOT NULL CHECK(length(store_name) BETWEEN 1 AND 120),
 area TEXT NOT NULL CHECK(length(area)<=120),
 observed_on TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected')),
 created_at INTEGER NOT NULL,
 reviewed_at INTEGER
);
CREATE INDEX reports_public ON price_reports(jan,status,observed_on DESC,created_at DESC);
CREATE INDEX reports_daily ON price_reports(contributor_id,created_at);
CREATE INDEX reports_pending ON price_reports(status,created_at);
