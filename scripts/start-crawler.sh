#!/bin/bash

# Legal Document Crawler - Start Script
# Quick start script for development and production use

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Default environment
ENVIRONMENT="development"
MONITORING=false
REBUILD=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -m|--monitoring)
            MONITORING=true
            shift
            ;;
        -r|--rebuild)
            REBUILD=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -e, --environment ENV    Set environment (development|production) [default: development]"
            echo "  -m, --monitoring         Start monitoring stack (Prometheus + Grafana)"
            echo "  -r, --rebuild           Rebuild application before starting"
            echo "  -h, --help              Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                      # Start in development mode"
            echo "  $0 -e production -m     # Start in production with monitoring"
            echo "  $0 -r                   # Rebuild and start"
            exit 0
            ;;
        *)
            print_error "Unknown option $1"
            exit 1
            ;;
    esac
done

echo "🚀 Starting Legal Document Crawler..."
echo "   Environment: $ENVIRONMENT"
echo "   Monitoring: $MONITORING"
echo "   Rebuild: $REBUILD"
echo ""

# Set environment file
ENV_FILE="docker-compose.yml"
if [ "$ENVIRONMENT" = "production" ]; then
    ENV_FILE="docker-compose.prod.yml"
fi

# Rebuild if requested
if [ "$REBUILD" = true ]; then
    echo "🔨 Rebuilding application..."
    docker-compose build --no-cache
    print_status "Application rebuilt"
fi

# Start services based on configuration
COMPOSE_CMD="docker-compose"
if [ "$MONITORING" = true ]; then
    COMPOSE_CMD="docker-compose --profile monitoring"
fi

echo "🐳 Starting services..."

# Start Solr first
$COMPOSE_CMD up -d solr

# Wait for Solr
echo "⏳ Waiting for Solr..."
timeout 60s bash -c 'while ! curl -sf http://localhost:8983/solr/admin/ping >/dev/null 2>&1; do sleep 2; done'

if [ $? -eq 0 ]; then
    print_status "Solr is ready"
else
    print_error "Solr startup timeout"
    exit 1
fi

# Initialize collection if needed
if ! curl -sf "http://localhost:8983/solr/admin/collections?action=LIST" | grep -q "legal-documents"; then
    echo "🗃️  Initializing Solr collection..."
    $COMPOSE_CMD up --no-deps solr-init
    print_status "Solr collection initialized"
fi

# Start application
$COMPOSE_CMD up -d crawler-app

# Wait for application
echo "⏳ Waiting for crawler application..."
timeout 60s bash -c 'while ! curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; do sleep 2; done'

if [ $? -eq 0 ]; then
    print_status "Crawler application is ready"
else
    print_error "Application startup timeout"
    docker-compose logs crawler-app
    exit 1
fi

# Start remaining services
$COMPOSE_CMD up -d

echo ""
echo "🎉 Legal Document Crawler started successfully!"
echo ""
echo "📊 Available services:"
printf "   %-20s %s\n" "Service" "URL"
printf "   %-20s %s\n" "────────────────────" "──────────────────────────────────────"
printf "   %-20s %s\n" "Crawler API" "http://localhost:8080/api/"
printf "   %-20s %s\n" "Health Check" "http://localhost:8080/actuator/health"
printf "   %-20s %s\n" "Solr Admin" "http://localhost:8983/solr/"
printf "   %-20s %s\n" "Nginx Proxy" "http://localhost/"

if [ "$MONITORING" = true ]; then
    printf "   %-20s %s\n" "Prometheus" "http://localhost:9090/"
    printf "   %-20s %s\n" "Grafana" "http://localhost:3000/ (admin/admin123)"
fi

echo ""
echo "🔧 Management commands:"
echo "   docker-compose ps                    # Show service status"
echo "   docker-compose logs -f               # Follow logs"
echo "   docker-compose logs -f crawler-app   # Follow app logs only"
echo "   docker-compose restart crawler-app   # Restart application"
echo "   docker-compose down                  # Stop all services"
echo ""
echo "🧪 Quick tests:"
echo "   curl http://localhost:8080/actuator/health"
echo "   curl http://localhost:8983/solr/legal-documents/admin/ping"
echo "   curl 'http://localhost:8080/api/documents/search?q=*:*&size=5'"
echo ""

# Show service status
echo "📋 Current service status:"
docker-compose ps

echo ""
print_status "Crawler is ready for legal document processing!"

# Optional: Trigger initial crawl
read -p "Start initial document crawl? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🏃 Starting initial crawl..."
    curl -X POST "http://localhost:8080/api/crawl/start" \
         -H "Content-Type: application/json" \
         -d '{"date":"2024-01-15","forceUpdate":false}' \
         && print_status "Initial crawl started" \
         || print_warning "Could not start initial crawl - check application logs"
fi