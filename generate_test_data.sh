#!/bin/bash

echo "🚀 Generiere Test-Daten für Grafana Dashboards..."

# Base URLs
API_BASE="http://localhost:8080"
NGINX_BASE="http://localhost:8888"

# Function to make API calls
make_requests() {
    local endpoint=$1
    local count=$2
    local delay=$3
    
    echo "📊 Sende $count Requests an $endpoint (Delay: ${delay}s)"
    
    for i in $(seq 1 $count); do
        curl -s "$endpoint" > /dev/null 2>&1
        if [ $delay -gt 0 ]; then
            sleep $delay
        fi
        echo -n "."
    done
    echo " ✅ Done"
}

# Generate different types of API traffic
echo "🔍 1. Status Check Requests..."
make_requests "$API_BASE/api/crawler/status" 20 1

echo ""
echo "🏥 2. Health Check Requests..."  
make_requests "$API_BASE/actuator/health" 15 0.5

echo ""
echo "📊 3. Metrics Requests..."
make_requests "$API_BASE/actuator/metrics" 10 2

echo ""
echo "🔧 4. Scheduled Tasks Info..."
make_requests "$API_BASE/actuator/scheduledtasks" 5 1

echo ""
echo "🔍 5. Document Search Requests..."
# Simuliere verschiedene Suchbegriffe
search_terms=("arbeitsrecht" "steuern" "verkehrsrecht" "mietrecht" "familienrecht")
for term in "${search_terms[@]}"; do
    make_requests "$API_BASE/api/crawler/search?query=$term" 3 1
done

echo ""
echo "🏛️ 6. Court-specific Document Requests..."
courts=("BAG" "BGH" "BSG" "BVerwG")
for court in "${courts[@]}"; do
    make_requests "$API_BASE/api/crawler/documents/$court?page=0&size=10" 5 0.8
done

echo ""
echo "📈 7. Nginx Proxy Traffic..."
make_requests "$NGINX_BASE/api/crawler/status" 30 0.3

echo ""
echo "🕷️ 8. Simuliere ein paar Crawl-Requests..."
dates=("2025-01-10" "2025-01-11" "2025-01-12")
for date in "${dates[@]}"; do
    echo "Crawling $date..."
    curl -X POST "$API_BASE/api/crawler/crawl?date=$date" -s > /dev/null 2>&1
    sleep 3
done

echo ""
echo "🔄 9. Mixed Traffic Pattern (simuliert echte Nutzung)..."
for round in $(seq 1 3); do
    echo "Round $round/3..."
    
    # Status checks (häufig)
    make_requests "$API_BASE/api/crawler/status" 8 0.2
    
    # Health checks (regelmäßig)  
    make_requests "$API_BASE/actuator/health" 3 0.5
    
    # Einzelne Suchen
    make_requests "$API_BASE/api/crawler/search?query=test$round" 2 1
    
    # Dokumentenabrufe
    make_requests "$API_BASE/api/crawler/documents/BAG?page=$round" 3 0.7
    
    sleep 2
done

echo ""
echo "✅ Test-Daten generiert! Die Grafana-Dashboards sollten jetzt Aktivität zeigen."
echo "🔗 Grafana: http://localhost:3000 (admin/admin123)"
echo "📊 Prometheus: http://localhost:9090"
echo ""

# Final status check
echo "📊 Aktueller Status:"
curl -s "$API_BASE/api/crawler/status" | jq