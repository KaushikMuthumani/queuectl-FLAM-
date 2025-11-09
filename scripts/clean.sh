#!/usr/bin/env bash
set -euo pipefail
pkill -f "queue-ctl.jar worker" 2>/dev/null || true
rm -f queuectl.db worker.out
echo "Cleaned DB and stopped workers."
