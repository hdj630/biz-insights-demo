#!/bin/bash
set -euo pipefail

echo "=== Setting up Business Insights Demo on EC2 ==="

# Install Amazon Corretto 17
echo "Installing Amazon Corretto 17..."
sudo yum install -y java-17-amazon-corretto-devel

# Install Maven
echo "Installing Maven..."
if ! command -v mvn &> /dev/null; then
    curl -sL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz \
        | sudo tar xz -C /opt/
    sudo ln -sf /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn
fi

# Install CloudWatch Agent
echo "Installing CloudWatch Agent..."
if ! command -v amazon-cloudwatch-agent-ctl &> /dev/null; then
    sudo yum install -y amazon-cloudwatch-agent
fi

# Download ADOT Java agent
ADOT_AGENT_PATH="/opt/aws-opentelemetry-agent.jar"
echo "Downloading ADOT Java agent..."
if [ ! -f "$ADOT_AGENT_PATH" ]; then
    sudo curl -sL -o "$ADOT_AGENT_PATH" \
        https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar
fi

# Configure CloudWatch Agent
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
echo "Configuring CloudWatch Agent..."
sudo cp "$PROJECT_DIR/config/amazon-cloudwatch-agent.json" /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json

# Build the project
echo "Building project..."
export JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
cd "$PROJECT_DIR"
mvn clean package -DskipTests

echo ""
echo "=== Setup complete ==="
echo "Run ./scripts/start-all.sh to start all services"
