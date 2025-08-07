#!/bin/bash

# Legal Document Crawler - Docker Setup Script
# This script sets up the complete Docker environment for the Solr-based crawler

set -e

echo "🚀 Setting up Legal Document Crawler with Docker & Solr..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

print_status "Docker and Docker Compose are available"

# Check if ports are available
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_warning "Port $1 is already in use. Please stop the service using this port or modify docker-compose.yml"
        return 1
    fi
    return 0
}

echo "🔍 Checking port availability..."
PORTS_AVAILABLE=true

if ! check_port 8080; then PORTS_AVAILABLE=false; fi
if ! check_port 8983; then PORTS_AVAILABLE=false; fi
if ! check_port 80; then PORTS_AVAILABLE=false; fi

if [ "$PORTS_AVAILABLE" = false ]; then
    read -p "Some ports are in use. Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

print_status "Port availability checked"

# Create necessary directories
echo "📁 Creating directory structure..."

mkdir -p docker/solr/configsets/legal-docs-config/conf
mkdir -p docker/nginx/ssl
mkdir -p docker/grafana/{dashboards,datasources}
mkdir -p data/legal-documents
mkdir -p logs

print_status "Directory structure created"

# Make scripts executable
echo "🔧 Setting up permissions..."

chmod +x docker/solr/init-collections.sh
chmod +x scripts/*.sh

print_status "Permissions set"

# Copy Docker-specific configuration
echo "📄 Preparing configurations..."

# Copy application configuration for Docker
cp docker/application-docker.yml src/main/resources/application-docker.yml

print_status "Configurations prepared"

# Build the application
echo "🔨 Building application..."

if ! ./mvnw clean package -DskipTests; then
    print_error "Application build failed"
    exit 1
fi

print_status "Application built successfully"

# Start the services
echo "🐳 Starting Docker services..."

# Pull images first
docker-compose pull

# Start Solr first
docker-compose up -d solr

# Wait for Solr to be ready
echo "⏳ Waiting for Solr to start..."
timeout 120s bash -c 'while ! curl -f http://localhost:8983/solr/admin/ping 2>/dev/null; do sleep 5; done'

if [ $? -eq 0 ]; then
    print_status "Solr is ready"
else
    print_error "Solr failed to start within timeout"
    exit 1
fi

# Initialize Solr collection
echo "🗃️  Initializing Solr collection..."
docker-compose up --no-deps solr-init

# Start the crawler application
echo "🚀 Starting crawler application..."
docker-compose up -d crawler-app

# Wait for application to be ready
echo "⏳ Waiting for application to start..."
timeout 120s bash -c 'while ! curl -f http://localhost:8080/actuator/health 2>/dev/null; do sleep 5; done'

if [ $? -eq 0 ]; then
    print_status "Crawler application is ready"
else
    print_error "Crawler application failed to start within timeout"
    exit 1
fi

# Start remaining services
echo "🌐 Starting remaining services..."
docker-compose up -d

echo ""
echo "🎉 Setup completed successfully!"
echo ""
echo "📊 Service URLs:"
echo "   • Crawler API:     http://localhost:8080/api/"
echo "   • Health Check:    http://localhost:8080/actuator/health"
echo "   • Solr Admin:      http://localhost:8983/solr/"
echo "   • Nginx Proxy:     http://localhost/"
echo ""
echo "🔍 Useful commands:"
echo "   • View logs:       docker-compose logs -f"
echo "   • Stop services:   docker-compose down"
echo "   • Restart:         docker-compose restart"
echo "   • Status:          docker-compose ps"
echo ""
echo "🧪 Test the setup:"
echo "   curl http://localhost:8080/actuator/health"
echo "   curl http://localhost:8983/solr/legal-documents/admin/ping"
echo ""

# Optional: Start monitoring stack
read -p "Start monitoring stack (Prometheus + Grafana)? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "📈 Starting monitoring stack..."
    docker-compose --profile monitoring up -d
    echo ""
    echo "📊 Additional URLs:"
    echo "   • Prometheus:      http://localhost:9090/"
    echo "   • Grafana:         http://localhost:3000/ (admin/admin123)"
fi

echo ""
print_status "Docker environment is ready for legal document crawling!"