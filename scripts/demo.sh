#!/usr/bin/env bash
set -euo pipefail

JAR="target/queue-ctl.jar"
DB="queuectl.db"
PORT=8088
PROOF_DIR="docs/proof"

mkdir -p "${PROOF_DIR}"

# === Utility ===
cleanup() {
  echo "🛑 Gracefully stopping all workers..."
  pkill -INT -f "queue-ctl.jar" || true
  sleep 1
  pkill -f "queue-ctl.jar" || true
}
trap cleanup EXIT

open_url() {
  local url="$1"
  for opener in xdg-open sensible-browser x-www-browser gnome-open open; do
    if command -v "$opener" >/dev/null 2>&1; then
      "$opener" "$url" >/dev/null 2>&1 && return 0
    fi
  done
  return 1
}

wait_for_dashboard() {
  local url="http://localhost:${PORT}/health"
  local tries=30
  while (( tries-- > 0 )); do
    if curl -s --max-time 1 "$url" | grep -qi "ok"; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

# === Begin Demo ===

echo "==> 🧹 Cleaning old data..."
rm -f ${DB} ${DB}-shm ${DB}-wal || true
rm -f ${PROOF_DIR}/* || true

echo "==> ⚙️ Building project..."
mvn -q -DskipTests package

# === CONFIG ===
echo -e "\n💾 CONFIGURATION SETUP"
java -jar "$JAR" config set max_retries 3
java -jar "$JAR" config set backoff_base 2
java -jar "$JAR" config get max_retries
sleep 1

# === ENQUEUE ===
echo -e "\n📦 ENQUEUE JOBS"
java -jar "$JAR" enqueue '{"id":"ok1","queue":"default","command":"echo OK"}'
java -jar "$JAR" enqueue '{"id":"bad1","queue":"default","command":"no_such_cmd","max_retries":2}'
java -jar "$JAR" enqueue '{"id":"slow1","queue":"default","command":"sleep 3"}'

echo -e "\n🕓 Checking STATUS after enqueue..."
java -jar "$JAR" status
sleep 1
java -jar "$JAR" list --state pending

# === START WORKERS ===
echo -e "\n👷 Starting workers (3) + Dashboard..."
(java -jar "$JAR" worker --start --queues default:3 --dashboard > "${PROOF_DIR}/worker.log" 2>&1) &
WPID=$!

if wait_for_dashboard; then
  echo "🌐 Dashboard running → http://localhost:${PORT}/status"
  open_url "http://localhost:${PORT}/status" || true
else
  echo "⚠️ Dashboard not ready yet. You can open it manually at http://localhost:${PORT}/status"
fi

# === LIVE STATUS UPDATES ===
echo -e "\n🔄 Monitoring Job Progress..."
for i in {1..8}; do
  echo -e "\n🕒 TICK $i — Current Queue State"
  java -jar "$JAR" status
  sleep 2
done

# === AFTER PROCESSING ===
echo -e "\n✅ FINAL STATUS SNAPSHOT"
java -jar "$JAR" status
java -jar "$JAR" list --state completed
java -jar "$JAR" list --state failed
java -jar "$JAR" list --state dead

# === DLQ ===
echo -e "\n💀 DLQ CHECK"
java -jar "$JAR" dlq list
sleep 1
echo -e "\n🔁 Retrying DLQ job 'bad1'..."
java -jar "$JAR" dlq retry bad1 || true
sleep 2
java -jar "$JAR" list --state pending

# === STOP WORKERS ===
echo -e "\n🛑 Stopping workers gracefully..."
kill -INT ${WPID} || true
sleep 2
pkill -f "queue-ctl.jar" || true

# === FINAL SNAPSHOTS ===
echo -e "\n📊 Capturing dashboard JSON..."
curl -s "http://localhost:${PORT}/status"  | tee "${PROOF_DIR}/dashboard_status.json"  >/dev/null || true
curl -s "http://localhost:${PORT}/jobs"    | tee "${PROOF_DIR}/dashboard_jobs.json"    >/dev/null || true
curl -s "http://localhost:${PORT}/dlq"     | tee "${PROOF_DIR}/dashboard_dlq.json"     >/dev/null || true

echo -e "\n✅ DEMO COMPLETE — Proof stored in ${PROOF_DIR}/"
