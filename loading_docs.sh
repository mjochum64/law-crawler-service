#!/bin/bash
# full-crawl.sh - Vollständiges Crawling aller verfügbaren Dokumente

echo "🚀 Starte vollständiges Crawling aller verfügbaren Dokumente..."

START_DATE="2025-07-01"  # Startdatum der Website
END_DATE=$(date -I)      # Heute

current_date=$START_DATE
total_days=$(( ( $(date -d "$END_DATE" +%s) - $(date -d "$START_DATE" +%s) ) / 86400 ))
day_count=0

echo "📅 Crawling Zeitraum: $START_DATE bis $END_DATE ($total_days Tage)"

while [ "$current_date" != $(date -I -d "$END_DATE + 1 day") ]; do
    day_count=$((day_count + 1))
    progress=$((day_count * 100 / total_days))

    echo "📊 Progress: $progress% - Tag $day_count/$total_days - Datum: $current_date"

    # Crawl für aktuelles Datum
    response=$(curl -s -X POST "http://localhost:8080/api/crawler/crawl?date=$current_date")

    if echo "$response" | grep -q "successfully"; then
        echo "✅ $current_date: Erfolgreich gestartet"
    else
        echo "⚠️ $current_date: Möglicherweise keine Dokumente verfügbar"
    fi

    # Status alle 10 Tage überprüfen
    if [ $((day_count % 10)) -eq 0 ]; then
        echo "📈 Zwischenstatus:"
        curl -s "http://localhost:8080/api/crawler/status" | jq '.totalDocuments, .downloadedDocuments, .failedDocuments'
    fi

    # Rate limiting - 30 Sekunden Pause
    sleep 30

    current_date=$(date -I -d "$current_date + 1 day")
done

  echo "🎉 Vollständiges Crawling abgeschlossen!"
  curl "http://localhost:8080/api/crawler/status"
