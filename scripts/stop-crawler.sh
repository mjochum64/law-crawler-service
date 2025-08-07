#!/bin/bash

# Legal Document Crawler - Stop Script
# Gracefully stops all Docker services

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

# Default options
REMOVE_VOLUMES=false
REMOVE_IMAGES=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--volumes)
            REMOVE_VOLUMES=true
            shift
            ;;
        -i|--images)
            REMOVE_IMAGES=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -v, --volumes    Remove volumes (WARNING: deletes all data)"
            echo "  -i, --images     Remove images after stopping"
            echo "  -h, --help       Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0               # Stop services"
            echo "  $0 -v            # Stop and remove data volumes"
            echo "  $0 -i            # Stop and remove images"
            exit 0
            ;;
        *)
            print_error "Unknown option $1"
            exit 1
            ;;
    esac
done

echo "🛑 Stopping Legal Document Crawler services..."

# Show current status
echo "📋 Current service status:"
docker-compose ps

# Stop services gracefully
echo ""
echo "⏸️  Stopping services..."

# Stop monitoring services first (if running)
if docker-compose --profile monitoring ps | grep -q "Up"; then
    echo "   Stopping monitoring services..."
    docker-compose --profile monitoring stop prometheus grafana
fi

# Stop main services
echo "   Stopping main services..."
docker-compose stop

print_status "All services stopped"

# Remove containers
echo "🗑️  Removing containers..."
docker-compose down

print_status "Containers removed"

# Remove volumes if requested
if [ "$REMOVE_VOLUMES" = true ]; then
    print_warning "Removing volumes - this will delete all data!"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down -v
        print_status "Volumes removed"
    else
        print_warning "Volume removal cancelled"
    fi
fi

# Remove images if requested
if [ "$REMOVE_IMAGES" = true ]; then
    echo "🗑️  Removing images..."
    
    # Remove project-specific images
    if docker images | grep -q legal-crawler; then
        docker rmi $(docker images | grep legal-crawler | awk '{print $3}') 2>/dev/null || true
    fi
    
    # Optionally remove pulled images
    read -p "Remove pulled images (Solr, Nginx, etc.)? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down --rmi all 2>/dev/null || true
        print_status "Images removed"
    fi
fi

# Clean up orphaned containers and networks
echo "🧹 Cleaning up..."
docker system prune -f >/dev/null 2>&1 || true

print_status "Cleanup completed"

# Show final status
echo ""
echo "📊 Final status:"
if docker ps -a | grep -q legal-crawler; then
    print_warning "Some legal-crawler containers still exist:"
    docker ps -a | grep legal-crawler || true
else
    print_status "All legal-crawler containers removed"
fi

if docker volume ls | grep -q legal-crawler; then
    echo "📁 Remaining volumes:"
    docker volume ls | grep legal-crawler || true
    echo "   Use 'docker volume rm <volume_name>' to remove specific volumes"
fi

if docker network ls | grep -q legal-crawler; then
    echo "🌐 Remaining networks:"
    docker network ls | grep legal-crawler || true
fi

echo ""
print_status "Legal Document Crawler stopped successfully!"

# Show restart instructions
echo ""
echo "🚀 To restart the crawler:"
echo "   ./scripts/start-crawler.sh"
echo ""
echo "🔧 To completely reset (remove all data):"
echo "   ./scripts/stop-crawler.sh --volumes"
echo "   ./scripts/setup-docker.sh"