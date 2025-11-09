#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/target/queue-ctl.jar"
DB="$ROOT/queuectl.db"
PORT="$(java -version >/dev/null 2>&1; echo 8088)"   # default, can be changed via config
PROOF_DIR="$ROOT/docs/proof"
WORKER_LOG="$PROOF_DIR/worker_live.log"

mkdir -p "$PROOF_DIR"

step() { echo -e "\n==> $*"; }

step "🧹 Cleaning up old data & leftover workers…"
pkill -INT -f "queue-ctl.jar" >/dev/null 2>&1 || true
sleep 1
rm -f "$DB" "$DB-shm" "$DB-wal" || true
rm -f "$WORKER_LOG" || true

step "⚙️ Building the project (mvn -q -DskipTests package)…"
( cd "$ROOT" && mvn -q -DskipTests package )
echo "✅ Jar ready: $JAR"

# --- One-time DB init to avoid Flyway/SQLite race on the very first CLI call
step "🗂️ Initializing database (first run migration)…"
java -jar "$JAR" status >/dev/null 2>&1 || true

step "📦 Enqueuing sample jobs…"
java -jar "$JAR" enqueue '{"id":"ok1","queue":"default","command":"echo OK","priority":10}'
sleep 0.2
java -jar "$JAR" enqueue '{"id":"ok2","queue":"default","command":"sleep 2","priority":5}'
sleep 0.2
java -jar "$JAR" enqueue '{"id":"bad1","queue":"default","command":"no_such_cmd","max_retries":2}'
sleep 0.2
java -jar "$JAR" enqueue '{"id":"slow1","queue":"default","command":"sleep 10","timeout_sec":1,"max_retries":1}'
sleep 0.2
# delayed
RUN_AT=$(date -u -d "+15 seconds" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+15S +"%Y-%m-%dT%H:%M:%SZ")
java -jar "$JAR" enqueue "{\"id\":\"later1\",\"queue\":\"default\",\"command\":\"echo FUTURE\",\"run_after\":\"$RUN_AT\"}"
sleep 0.2
# tiny cron demo (materializer will create instances)
java -jar "$JAR" enqueue '{"id":"cron-hello","queue":"default","command":"echo HI","cron":"*/1 * * * *"}'

step "🚦 Capturing initial queue state…"
java -jar "$JAR" status | tee "$PROOF_DIR/status_initial.txt"
java -jar "$JAR" list --state pending | tee "$PROOF_DIR/pending_initial.txt"

step "👷 Starting workers on 'default:3' (with dashboard)…"
( java -jar "$JAR" worker --start --queues default:3 --dashboard > "$WORKER_LOG" 2>&1 ) &
WPID=$!
echo "✅ Worker process PID: $WPID"

# Wait for dashboard to come up
step "🌐 Waiting for dashboard (http://localhost:$PORT)…"
for i in {1..15}; do
  if curl -fsS "http://localhost:${PORT}/health" >/dev/null 2>&1; then
    echo "  ok"
    break
  fi
  sleep 1
done

# Try opening the browser (best effort)
if command -v xdg-open >/dev/null 2>&1; then
  xdg-open "http://localhost:${PORT}/" >/dev/null 2>&1 || true
elif command -v open >/dev/null 2>&1; then
  open "http://localhost:${PORT}/" || true
elif command -v start >/dev/null 2>&1; then
  start "http://localhost:${PORT}/" || true
else
  echo "🔗 Open manually: http://localhost:${PORT}/"
fi

step "📡 Live status polling (see transitions)…"
for i in {1..5}; do
  echo "📍 Tick #$i"
  java -jar "$JAR" status | tee "$PROOF_DIR/status_${i}.txt"
  java -jar "$JAR" list --state processing | tee "$PROOF_DIR/processing_${i}.txt"
  java -jar "$JAR" list --state completed | tee "$PROOF_DIR/completed_${i}.txt"
  java -jar "$JAR" list --state failed | tee "$PROOF_DIR/failed_${i}.txt"
  sleep 3
done

step "☠️ DLQ check (post retries/timeouts)…"
java -jar "$JAR" dlq list | tee "$PROOF_DIR/dlq_after_retries.txt" || true

step "⏳ Waiting for delayed job to become eligible…"
sleep 15
java -jar "$JAR" status | tee "$PROOF_DIR/status_after_delay.txt"
java -jar "$JAR" list --state pending | tee "$PROOF_DIR/pending_after_delay.txt"

step "📜 Capturing example logs for completed/timeout jobs…"
# These may or may not exist depending on timing, don't fail the script
( java -jar "$JAR" logs ok1 | tee "$PROOF_DIR/logs_ok1.txt" ) || true
( java -jar "$JAR" logs slow1 | tee "$PROOF_DIR/logs_slow1.txt" ) || true

step "🌐 Capturing dashboard JSON snapshots…"
( curl -s "http://localhost:${PORT}/health"  | tee "$PROOF_DIR/dashboard_health.txt" ) || true
( curl -s "http://localhost:${PORT}/status"  | tee "$PROOF_DIR/dashboard_status.json" ) || true
( curl -s "http://localhost:${PORT}/jobs"    | tee "$PROOF_DIR/dashboard_jobs.json" ) || true
( curl -s "http://localhost:${PORT}/jobs?state=pending" | tee "$PROOF_DIR/dashboard_jobs_pending.json" ) || true

step "⚡ Bonus: Create 'fast' queue & process priority jobs there…"
java -jar "$JAR" queue create fast --rate 200 --concurrency 3 || true
for n in 1 2 3 4 5; do
  java -jar "$JAR" enqueue "{\"id\":\"p${n}\",\"queue\":\"fast\",\"command\":\"echo P${n}\",\"priority\":$((11-n))}"
  sleep 0.1
done

step "👷 Spawning fast workers (fast:3, no extra dashboard)…"
( java -jar "$JAR" worker --start --queues fast:3 >> "$WORKER_LOG" 2>&1 ) &
FPID=$!
echo "✅ Fast worker PID: $FPID"

sleep 3
java -jar "$JAR" status | tee "$PROOF_DIR/status_after_fast.txt"

step "🧾 Final proof snapshot…"
java -jar "$JAR" status | tee "$PROOF_DIR/status_final.txt"
java -jar "$JAR" dlq list | tee "$PROOF_DIR/dlq_final.txt" || true

step "🛑 Stopping workers…"
kill -INT "$FPID" >/dev/null 2>&1 || true
kill -INT "$WPID" >/dev/null 2>&1 || true
sleep 1
pkill -INT -f "queue-ctl.jar" >/dev/null 2>&1 || true

step "📁 Proof artifacts saved to: $PROOF_DIR/"
ls -lh "$PROOF_DIR"
echo "✅ Done."
