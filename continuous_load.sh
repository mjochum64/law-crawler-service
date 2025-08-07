#!/bin/bash

echo "🔄 Starte kontinuierliche Last für das Dashboard-Monitoring..."
echo "🛑 Stoppe mit CTRL+C"

# Funktion zum Generieren unterschiedlicher API-Calls
generate_load() {
    while true; do
        # Status checks (häufig)
        curl -s http://localhost:8080/api/crawler/status > /dev/null &
        
        # Health checks  
        curl -s http://localhost:8080/actuator/health > /dev/null &
        
        # Zufällige Suchen
        queries=("arbeitsrecht" "mietrecht" "verkehrsrecht" "steuerrecht" "familienrecht")
        random_query=${queries[$RANDOM % ${#queries[@]}]}
        curl -s "http://localhost:8080/api/crawler/search?query=$random_query" > /dev/null &
        
        # Gerichts-spezifische Abfragen
        courts=("BAG" "BGH" "BSG" "BVerwG")
        random_court=${courts[$RANDOM % ${#courts[@]}]}
        curl -s "http://localhost:8080/api/crawler/documents/$random_court?page=0&size=5" > /dev/null &
        
        # Metrics
        curl -s http://localhost:8080/actuator/metrics > /dev/null &
        
        # Warte zwischen 1-3 Sekunden
        sleep $((1 + RANDOM % 3))
    done
}

# Starte Load Generation im Hintergrund
generate_load &
LOAD_PID=$!

echo "✅ Load-Generator gestartet (PID: $LOAD_PID)"
echo "📊 Zugriff auf:"
echo "   - Grafana: http://localhost:3000 (admin/admin123)"
echo "   - Prometheus: http://localhost:9090"
echo "   - API Status: http://localhost:8080/api/crawler/status"

# Warte auf CTRL+C
trap "echo '🛑 Stoppe Load-Generator...'; kill $LOAD_PID; exit" INT

# Zeige Live-Status alle 10 Sekunden
while true; do
    sleep 10
    echo "📊 $(date): Load läuft - Dashboard sollte Aktivität zeigen"
done