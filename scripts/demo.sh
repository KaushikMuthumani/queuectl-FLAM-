#!/usr/bin/env bash
set -euo pipefail

JAR="target/queue-ctl.jar"
DB="queuectl.db"

# ---------- Pretty printing ----------
C_RESET="\033[0m"
C_BOLD="\033[1m"
C_GREEN="\033[32m"
C_YELLOW="\033[33m"
C_BLUE="\033[34m"
C_RED="\033[31m"
say() { printf "${C_BOLD}${C_BLUE}👉 %s${C_RESET}\n" "$*"; }
ok()  { printf "${C_BOLD}${C_GREEN}✅ %s${C_RESET}\n" "$*"; }
warn(){ printf "${C_BOLD}${C_YELLOW}⚠️  %s${C_RESET}\n" "$*"; }
err() { printf "${C_BOLD}${C_RED}❌ %s${C_RESET}\n" "$*"; }

ensure_jar() {
  if [[ ! -f "$JAR" ]]; then
    say "Jar not found. Building project…"
    mvn -q -DskipTests package
  fi
  ok "Jar ready: $JAR"
}

clean_env() {
  say "Cleaning previous run (DB + leftover worker)…"
  pkill -f "queue-ctl.jar worker" 2>/dev/null || true
  rm -f "$DB"
  ok "Clean slate."
}

start_workers() {
  local queues="$1"   # e.g., "default:3"
  local dashboard="${2:-false}"
  say "Starting workers: $queues (dashboard=$dashboard)…"
  # Run in background; redirect logs to a file
  nohup java -jar "$JAR" worker --start --queues "$queues" $( [[ "$dashboard" == "true" ]] && echo --dashboard ) \
      > worker.out 2>&1 &
  WORKER_PID=$!
  ok "Worker process PID: $WORKER_PID"
  sleep 1
}

stop_workers() {
  if [[ -n "${WORKER_PID:-}" ]]; then
    say "Stopping workers (PID=$WORKER_PID)…"
    kill "$WORKER_PID" 2>/dev/null || true
    sleep 1
    ok "Workers stopped."
  fi
}

enqueue() {
  local json="$1"
  say "Enqueue: $json"
  set +e
  java -jar "$JAR" enqueue "$json"
  local rc=$?
  set -e
  if [[ $rc -ne 0 ]]; then warn "enqueue failed (expected if duplicate id or bad command)"; fi
}

status() {
  say "Status:"
  java -jar "$JAR" status || true
}

list_state() {
  local st="$1"
  say "List $st:"
  java -jar "$JAR" list --state "$st" --limit 50 || true
}

logs() {
  local id="$1"
  say "Logs for $id:"
  java -jar "$JAR" logs "$id" --limit 20 || true
}

dlq_list() {
  say "DLQ list:"
  java -jar "$JAR" dlq list || true
}

dlq_retry() {
  local id="$1"
  say "DLQ retry: $id"
  java -jar "$JAR" dlq retry "$id" || true
}

dash_check() {
  say "Dashboard checks:"
  curl -sS http://localhost:8088/health | sed 's/^/  /'
  echo
  curl -sS http://localhost:8088/status | sed 's/^/  /'
  echo
  curl -sS http://localhost:8088/jobs | sed 's/^/  /'
  echo
}

delay_sec() {
  local s="$1"
  printf "   (sleep %ss)\n" "$s"
  sleep "$s"
}

main() {
  ensure_jar
  clean_env

  # ---------- Happy-path + failing jobs ----------
  enqueue '{"id":"ok1","queue":"default","command":"echo OK","priority":10}'
  enqueue '{"id":"ok2","queue":"default","command":"sleep 2","priority":5}'
  enqueue '{"id":"bad1","queue":"default","command":"no_such_cmd","max_retries":2}'

  # Timeout demo (kill after 1s)
  enqueue '{"id":"slow1","queue":"default","command":"sleep 10","timeout_sec":1,"max_retries":1}'

  # Delayed job (15s from now, UTC)
  RUN_AT=$(date -u -d "+15 seconds" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+15S +"%Y-%m-%dT%H:%M:%SZ")
  enqueue "{\"id\":\"later1\",\"queue\":\"default\",\"command\":\"echo FUTURE\",\"run_after\":\"$RUN_AT\"}"

  # Cron-like job (materialized each minute)
  enqueue '{"id":"cron-hello","queue":"default","command":"echo HI","cron":"*/1 * * * *"}'

  status
  list_state pending

  # ---------- Start workers + dashboard ----------
  start_workers "default:3" true
  say "Let workers process immediate jobs…"
  delay_sec 5
  status

  say "Check DLQ after retries/timeouts…"
  dlq_list

  # Retry from DLQ if present
  # (Pick the first DLQ id automatically)
  DLQ_FIRST=$(java -jar "$JAR" dlq list | awk '/^- id=/{print $2}' | head -n1 || true)
  if [[ -n "$DLQ_FIRST" ]]; then
    dlq_retry "$DLQ_FIRST"
  else
    warn "No DLQ entries to retry right now."
  fi

  say "Wait for delayed job to become eligible…"
  delay_sec 15
  status
  list_state pending

  # ---------- Logs ----------
  logs ok1
  logs slow1 || true

  # ---------- Dashboard API checks ----------
  dash_check

  # ---------- Priority demo (fast queue) ----------
  say "Create a 'fast' queue and enqueue priority jobs…"
  java -jar "$JAR" queue create fast --rate 200 --concurrency 4
  for i in 1 2 3 4 5; do
    PRIO=$((10 - i))
    enqueue "{\"id\":\"p$i\",\"queue\":\"fast\",\"command\":\"echo P$i\",\"priority\":$PRIO}"
  done

  say "Add fast workers (alongside default)…"
  start_workers "fast:3" false
  delay_sec 3
  status

  ok "Demo complete! View dashboard at: http://localhost:8088/"
  ok "Worker logs: ./worker.out"
}

trap 'stop_workers || true' EXIT
main "$@"
