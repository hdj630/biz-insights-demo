#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TRAFFIC_DIR="$PROJECT_DIR/traffic-generator/target"
TRAFFIC_JAR="$TRAFFIC_DIR/traffic-generator-1.0.0-SNAPSHOT.jar"
TRAFFIC_LIB="$TRAFFIC_DIR/lib"
ADOT_AGENT="/opt/aws-opentelemetry-agent.jar"

export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto

REQUESTS=${1:-50}
DELAY=${2:-2000}
FAIL_AT=${3:-}
CANCEL_RATIO=${4:-0.2}

echo "Running traffic generator: requests=$REQUESTS delay=$DELAY failAt=${FAIL_AT:-none} cancelRatio=$CANCEL_RATIO"

JAVA_TOOL_OPTIONS="-javaagent:$ADOT_AGENT" \
OTEL_SERVICE_NAME=traffic-generator \
OTEL_METRICS_EXPORTER=none \
OTEL_LOGS_EXPORTER=none \
OTEL_AWS_APPLICATION_SIGNALS_ENABLED=true \
OTEL_AWS_APPLICATION_SIGNALS_EXPORTER_ENDPOINT=http://localhost:4316/v1/metrics \
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4316/v1/traces \
OTEL_PROPAGATORS=tracecontext,baggage,b3,xray \
OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CONTROLLER_TELEMETRY_ENABLED=true \
"$JAVA_HOME/bin/java" -cp "$TRAFFIC_JAR:$TRAFFIC_LIB/*" com.example.bizinsights.traffic.TrafficGeneratorApp \
    --requests="$REQUESTS" \
    --delay="$DELAY" \
    --fail-at="$FAIL_AT" \
    --cancel-ratio="$CANCEL_RATIO" \
    --browse-url=http://localhost:8081/process \
    --fulfillment-url=http://localhost:8085/cancel
