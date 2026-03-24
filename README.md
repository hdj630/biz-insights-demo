# Business Insights Demo

A sample Java microservices app demonstrating ADOT business insights tracing with custom span attributes. Deploys to a single EC2 instance with CloudWatch Agent forwarding traces to AWS X-Ray.

## Architecture

```
EC2 Instance
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  Traffic Generator ──► browse:8081 → add-to-cart:8082               │
│         │                 → check-inventory:8083 → payment:8084     │
│         │                     → fulfillment:8085                     │
│         │                          │ (cancel)                        │
│         └─(cancel)────────────► refund:8086                          │
│                                                                      │
│  All services ──(OTLP)──► CloudWatch Agent :4316 ──► X-Ray          │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

All 6 stages run the **same Spring Boot JAR** with different environment variables. The ADOT Java agent auto-instruments HTTP server/client spans. A Servlet filter adds custom business attributes to each server span:

- `aws.tracing.biz.id` — orderId
- `aws.tracing.biz.workflow` — `ecommerce-purchase`
- `aws.tracing.biz.stage` — stage name (browse, add-to-cart, etc.)

## Prerequisites

- EC2 instance (t3.medium+) with Amazon Linux 2023
- IAM role with `CloudWatchAgentServerPolicy` and `AWSXRayDaemonWriteAccess`

## Quick Start

```bash
# 1. Clone and setup
git clone <this-repo>
cd biz-insights-demo
./scripts/setup-ec2.sh

# 2. Start all services + CloudWatch Agent
./scripts/start-all.sh

# 3. Test manually
curl -s -X POST http://localhost:8081/process -H "X-Order-Id: test-001" | jq .

# 4. Run traffic generator
./scripts/run-traffic.sh 50 2000 "" 0.2
```

## Traffic Generator Usage

```bash
./scripts/run-traffic.sh <requests> <delay_ms> <fail_at_stage> <cancel_ratio>
```

| Arg | Default | Description |
|-----|---------|-------------|
| requests | 50 | Number of workflow requests |
| delay_ms | 2000 | Delay between requests (ms) |
| fail_at_stage | (none) | Stage to inject failure: `browse`, `add-to-cart`, `check-inventory`, `payment`, `fulfillment` |
| cancel_ratio | 0.2 | Fraction of successful orders to cancel (triggers refund) |

### Examples

```bash
# Full workflow, no failures, 20% cancellations
./scripts/run-traffic.sh 50 2000 "" 0.2

# All requests fail at payment
./scripts/run-traffic.sh 50 2000 payment 0.0

# Fast traffic, fail at inventory, no cancellations
./scripts/run-traffic.sh 100 500 check-inventory 0.0
```

## Manual Testing

```bash
# Full workflow
curl -X POST http://localhost:8081/process -H "X-Order-Id: order-001"

# Workflow with failure injection
curl -X POST http://localhost:8081/process -H "X-Order-Id: order-002" -H "X-Fail-At-Stage: payment"

# Cancel an existing order (triggers refund)
curl -X POST http://localhost:8085/cancel -H "X-Order-Id: order-001"
```

## Service Ports

| Stage | Port |
|-------|------|
| browse | 8081 |
| add-to-cart | 8082 |
| check-inventory | 8083 |
| payment | 8084 |
| fulfillment | 8085 |
| refund | 8086 |

## Stop Services

```bash
./scripts/stop-all.sh
```

## Building

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
mvn clean package -DskipTests
```
