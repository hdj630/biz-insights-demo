#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TRAFFIC_DIR="$PROJECT_DIR/traffic-generator/target"
TRAFFIC_JAR="$TRAFFIC_DIR/traffic-generator-1.0.0-SNAPSHOT.jar"
TRAFFIC_LIB="$TRAFFIC_DIR/lib"
ADOT_AGENT="/opt/aws-opentelemetry-agent.jar"

export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto

usage() {
    echo "Usage:"
    echo "  Batch mode:      $0 batch <requests> <delay_ms> [fail_at] [cancel_ratio]"
    echo "  Continuous mode:  $0 continuous <tps> [duration_secs] [fail_at] [cancel_ratio]"
    echo ""
    echo "Examples:"
    echo "  $0 batch 50 2000                  # 50 requests, 2s delay"
    echo "  $0 batch 50 2000 payment 0.0      # 50 requests, all fail at payment"
    echo "  $0 continuous 5                    # 5 TPS, run until Ctrl+C"
    echo "  $0 continuous 10 300              # 10 TPS for 5 minutes"
    echo "  $0 continuous 5 0 payment 0.1     # 5 TPS, fail at payment, 10% cancel"
    exit 1
}

[ $# -lt 1 ] && usage

MODE=$1
shift

OTEL_ENVS=(
    JAVA_TOOL_OPTIONS="-javaagent:$ADOT_AGENT"
    OTEL_SERVICE_NAME=traffic-generator
    OTEL_METRICS_EXPORTER=none
    OTEL_LOGS_EXPORTER=none
    OTEL_AWS_APPLICATION_SIGNALS_ENABLED=true
    OTEL_AWS_APPLICATION_SIGNALS_EXPORTER_ENDPOINT=http://localhost:4316/v1/metrics
    OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
    OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4316/v1/traces
    OTEL_PROPAGATORS=tracecontext,baggage,b3,xray
    OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CONTROLLER_TELEMETRY_ENABLED=true
)

case "$MODE" in
    batch)
        REQUESTS=${1:-50}
        DELAY=${2:-2000}
        FAIL_AT=${3:-}
        CANCEL_RATIO=${4:-0.2}
        echo "Batch mode: requests=$REQUESTS delay=$DELAY failAt=${FAIL_AT:-none} cancelRatio=$CANCEL_RATIO"
        env "${OTEL_ENVS[@]}" \
        "$JAVA_HOME/bin/java" -cp "$TRAFFIC_JAR:$TRAFFIC_LIB/*" com.example.bizinsights.traffic.TrafficGeneratorApp \
            --requests="$REQUESTS" --delay="$DELAY" --fail-at="$FAIL_AT" --cancel-ratio="$CANCEL_RATIO" \
            --browse-url=http://localhost:8081/process --fulfillment-url=http://localhost:8085/cancel
        ;;
    continuous)
        TPS=${1:-5}
        DURATION=${2:-0}
        FAIL_AT=${3:-}
        CANCEL_RATIO=${4:-0.2}
        DURATION_STR=$( [ "$DURATION" -eq 0 ] && echo "until Ctrl+C" || echo "${DURATION}s" )
        echo "Continuous mode: tps=$TPS duration=$DURATION_STR failAt=${FAIL_AT:-none} cancelRatio=$CANCEL_RATIO"
        env "${OTEL_ENVS[@]}" \
        "$JAVA_HOME/bin/java" -cp "$TRAFFIC_JAR:$TRAFFIC_LIB/*" com.example.bizinsights.traffic.TrafficGeneratorApp \
            --tps="$TPS" --duration="$DURATION" --fail-at="$FAIL_AT" --cancel-ratio="$CANCEL_RATIO" \
            --browse-url=http://localhost:8081/process --fulfillment-url=http://localhost:8085/cancel
        ;;
    *)
        usage
        ;;
esac
