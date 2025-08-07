#!/bin/bash

echo "🚀 Generiere kontinuierliche Last für Monitoring..."

# API Base URL
API_BASE="http://localhost:8080"

# Funktion für kontinuierliche API-Calls
while true; do
    echo "📊 $(date): Generiere API-Traffic..."
    
    # Status checks (häufig)
    curl -s "$API_BASE/api/crawler/status" > /dev/null &
    
    # Health checks
    curl -s "$API_BASE/actuator/health" > /dev/null &
    
    # Metrics
    curl -s "$API_BASE/actuator/prometheus" > /dev/null &
    
    # Suche nach verschiedenen Begriffen
    search_terms=("arbeitsrecht" "mietrecht" "verkehrsrecht" "steuerrecht")
    term=${search_terms[$((RANDOM % ${#search_terms[@]}))]}
    curl -s "$API_BASE/api/crawler/search?query=$term" > /dev/null &
    
    # Verschiedene Gerichtsabfragen
    courts=("BAG" "BGH" "BSG" "BVerwG")
    court=${courts[$((RANDOM % ${#courts[@]}))]}
    curl -s "$API_BASE/api/crawler/documents/$court?page=0&size=5" > /dev/null &
    
    # Warte 2-5 Sekunden
    sleep $((2 + RANDOM % 4))
done