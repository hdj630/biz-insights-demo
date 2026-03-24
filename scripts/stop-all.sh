#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PID_DIR="$PROJECT_DIR/.pids"

echo "Stopping all services..."

for pid_file in "$PID_DIR"/*.pid; do
    if [ -f "$pid_file" ]; then
        service_name=$(basename "$pid_file" .pid)
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "  Stopping $service_name (PID: $pid)..."
            kill "$pid"
        else
            echo "  $service_name (PID: $pid) already stopped"
        fi
        rm -f "$pid_file"
    fi
done

echo "Stopping CloudWatch Agent..."
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a stop 2>/dev/null || true

echo "=== All services stopped ==="
