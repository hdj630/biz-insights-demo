#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PID_DIR="$PROJECT_DIR/.pids"
LOG_DIR="$PROJECT_DIR/logs"
SERVICE_JAR="$PROJECT_DIR/workflow-service/target/workflow-service-1.0.0-SNAPSHOT.jar"
ADOT_AGENT="/opt/aws-opentelemetry-agent.jar"

mkdir -p "$PID_DIR" "$LOG_DIR"

export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto

# Common OTEL environment variables
export OTEL_METRICS_EXPORTER=none
export OTEL_LOGS_EXPORTER=none
export OTEL_AWS_APPLICATION_SIGNALS_ENABLED=true
export OTEL_AWS_APPLICATION_SIGNALS_EXPORTER_ENDPOINT=http://localhost:4316/v1/metrics
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4316/v1/traces
export OTEL_PROPAGATORS=tracecontext,baggage,b3,xray
export OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CONTROLLER_TELEMETRY_ENABLED=true

# Start CloudWatch Agent
echo "Starting CloudWatch Agent..."
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
    -a fetch-config \
    -m ec2 \
    -c file:"$PROJECT_DIR/config/amazon-cloudwatch-agent.json" \
    -s

start_service() {
    local stage_name=$1
    local port=$2
    local next_url=$3
    local cancel_url=${4:-}

    echo "Starting $stage_name on port $port..."

    JAVA_TOOL_OPTIONS="-javaagent:$ADOT_AGENT" \
    OTEL_SERVICE_NAME="${stage_name}-service" \
    STAGE_NAME="$stage_name" \
    STAGE_PORT="$port" \
    NEXT_STAGE_URL="$next_url" \
    CANCEL_STAGE_URL="$cancel_url" \
    "$JAVA_HOME/bin/java" -jar "$SERVICE_JAR" \
        > "$LOG_DIR/${stage_name}.log" 2>&1 &

    echo $! > "$PID_DIR/${stage_name}.pid"
    echo "  -> PID: $!"
}

start_service "browse"          8081 "http://localhost:8082/process"
start_service "add-to-cart"     8082 "http://localhost:8083/process"
start_service "check-inventory" 8083 "http://localhost:8084/process"
start_service "payment"         8084 "http://localhost:8085/process"
start_service "fulfillment"     8085 "" "http://localhost:8086/process"
start_service "refund"          8086 ""

echo ""
echo "Waiting for services to be ready..."
for port in 8081 8082 8083 8084 8085 8086; do
    for i in $(seq 1 30); do
        if curl -s "http://localhost:$port/health" > /dev/null 2>&1; then
            echo "  -> Port $port ready"
            break
        fi
        if [ "$i" -eq 30 ]; then
            echo "  -> WARNING: Port $port not ready after 30s"
        fi
        sleep 1
    done
done

echo ""
echo "=== All services started ==="
echo "Test: curl -X POST http://localhost:8081/process -H 'X-Order-Id: test-001'"
