# queue-ctl

A production-style Java CLI background job queue:
- SQLite persistence with Flyway
- Multi-worker with leases & graceful shutdown
- Retries (exponential backoff + jitter)
- DLQ
- Priority, timeouts, delayed & cron-like jobs
- Idempotency keys
- Logs capture
- Rate limiting per queue
- Minimal dashboard (/ /status /jobs)

## Build

```bash
mvn -q -DskipTests package
