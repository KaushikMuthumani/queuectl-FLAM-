PRAGMA journal_mode=WAL;

CREATE TABLE IF NOT EXISTS jobs (
  id TEXT PRIMARY KEY,
  queue TEXT NOT NULL DEFAULT 'default',
  command TEXT NOT NULL,
  args TEXT,
  state TEXT NOT NULL CHECK (state IN ('pending','processing','completed','failed','dead')),
  attempts INTEGER NOT NULL DEFAULT 0,
  max_retries INTEGER NOT NULL DEFAULT 3,
  priority INTEGER NOT NULL DEFAULT 0,
  timeout_sec INTEGER NOT NULL DEFAULT 60,
  idempotency_key TEXT,
  run_after TEXT NOT NULL,
  cron TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  lease_until TEXT,
  worker_id TEXT,
  last_exit_code INTEGER,
  last_error TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_jobs_idem ON jobs(idempotency_key, queue) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jobs_state_q_time ON jobs(state, queue, run_after, priority DESC, created_at);

CREATE TABLE IF NOT EXISTS job_deps (
  job_id TEXT NOT NULL,
  depends_on TEXT NOT NULL,
  PRIMARY KEY(job_id, depends_on)
);

CREATE TABLE IF NOT EXISTS dlq (
  id TEXT PRIMARY KEY,
  queue TEXT NOT NULL,
  command TEXT NOT NULL,
  attempts INTEGER NOT NULL,
  last_exit_code INTEGER,
  last_error TEXT,
  moved_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS job_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  job_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('stdout','stderr','system')),
  content TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_job_logs_job ON job_logs(job_id, created_at);

CREATE TABLE IF NOT EXISTS config (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS queues (
  name TEXT PRIMARY KEY,
  rate_limit_per_sec INTEGER NOT NULL DEFAULT 50,
  concurrency INTEGER NOT NULL DEFAULT 2,
  paused INTEGER NOT NULL DEFAULT 0
);

INSERT INTO config(key, value) VALUES
  ('max_retries','3'),
  ('backoff_base','2'),
  ('job_timeout_sec','60'),
  ('dashboard_port','8088')
ON CONFLICT(key) DO NOTHING;

INSERT INTO queues(name, rate_limit_per_sec, concurrency, paused) VALUES ('default', 50, 2, 0)
ON CONFLICT(name) DO NOTHING;
