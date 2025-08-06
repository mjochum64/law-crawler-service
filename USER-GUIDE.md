> Wie kann ich Dokumente nun für einen bestimmten zeitraum abrufen

● 📅 ZEITRAUM-BASIERTES CRAWLING - Anleitung

  Das System bietet mehrere Methoden für zeitraumbasiertes Abrufen von Dokumenten:

  🚀 1. Sofortiges Crawling für spezifische Daten

  Einzelnes Datum crawlen:

  # Für heute (gestern wird standardmäßig gecrawlt)
  curl -X POST "http://localhost:8080/api/crawler/crawl"

  # Für ein bestimmtes Datum
  curl -X POST "http://localhost:8080/api/crawler/crawl?date=2024-08-01"

  # Mit Force Update (bereits heruntergeladene Dokumente erneut verarbeiten)
  curl -X POST "http://localhost:8080/api/crawler/crawl?date=2024-08-01&forceUpdate=true"

  Status überwachen:

  curl http://localhost:8080/api/crawler/status

  📅 2. Zeitraum-Crawling (mehrere Tage)

  Für größere Zeiträume können Sie ein einfaches Script verwenden:

  #!/bin/bash
  # Crawl-Script für Zeitraum (crawl-range.sh)

  START_DATE="2024-07-01"
  END_DATE="2024-08-06"

  current_date=$START_DATE

  while [ "$current_date" != $(date -I -d "$END_DATE + 1 day") ]; do
      echo "Crawling für Datum: $current_date"

      curl -X POST "http://localhost:8080/api/crawler/crawl?date=$current_date"

      # 30 Sekunden warten zwischen den Requests
      sleep 30

      # Nächster Tag
      current_date=$(date -I -d "$current_date + 1 day")
  done

  echo "Crawling für Zeitraum $START_DATE bis $END_DATE abgeschlossen"

  ⏰ 3. Automatisches Scheduling (bereits konfiguriert)

  Das System ist bereits für automatisches Crawling konfiguriert:

  crawler:
    scheduled:
      enabled: true
      daily-cron: "0 0 6 * * ?"      # Täglich um 6:00 Uhr
      weekly-cron: "0 0 2 * * SUN"   # Sonntag um 2:00 Uhr  
      retry-cron: "0 0 */6 * * ?"     # Alle 6 Stunden Retry

  📊 4. Abgerufene Dokumente verwalten

  Status und Statistiken:

  # System-Status
  curl http://localhost:8080/api/crawler/status

  # Statistiken nach Gerichten
  curl http://localhost:8080/api/crawler/statistics/courts

  Dokumente nach Gericht abrufen:

  # BGH Dokumente (erste 20)
  curl "http://localhost:8080/api/crawler/documents/BGH"

  # Mit Pagination
  curl "http://localhost:8080/api/crawler/documents/BGH?page=0&size=50"

  # Andere Gerichte
  curl "http://localhost:8080/api/crawler/documents/BAG"  # Bundesarbeitsgericht
  curl "http://localhost:8080/api/crawler/documents/BSG"  # Bundessozialgericht
  curl "http://localhost:8080/api/crawler/documents/BVerwG"  # Bundesverwaltungsgericht

  Dokumente durchsuchen:

  # Textsuche in Titeln
  curl "http://localhost:8080/api/crawler/search?query=Arbeitsrecht"

  💾 5. Gespeicherte Dokumente finden

  Die XML-Dateien werden strukturiert gespeichert:

  ./legal-documents/
  ├── bgh/2024/08/KORE600068723.xml
  ├── bag/2024/07/KARE500041892.xml
  ├── bsg/2024/06/KSRE780023456.xml
  └── bverwg/2024/05/WBRE920012345.xml

  Dateisystem durchsuchen:

  # Alle XML-Dateien für August 2024
  find ./legal-documents -path "*/2024/08/*.xml"

  # BGH Dokumente für 2024
  find ./legal-documents/bgh/2024 -name "*.xml"

  # Anzahl Dokumente pro Gericht
  find ./legal-documents -name "*.xml" | cut -d/ -f3 | sort | uniq -c

  🔄 6. Fehlerbehandlung und Retry

  # Fehlgeschlagene Dokumente erneut versuchen
  curl -X POST "http://localhost:8080/api/crawler/retry-failed"

  📈 7. Monitoring und Health Check

  # Application Health
  curl http://localhost:8080/actuator/health

  # Detailed Health Info
  curl http://localhost:8080/actuator/health | jq .

  # System Metrics
  curl http://localhost:8080/actuator/metrics

  ⚙️ 8. Erweiterte Konfiguration

  Sie können das Crawling-Verhalten in application.yml anpassen:

  crawler:
    rate-limit-ms: 2000        # Pause zwischen Requests (2 Sekunden)
    base-url: https://www.rechtsprechung-im-internet.de
    storage:
      base-path: ./legal-documents

    scheduled:
      enabled: true
      days-back: 7              # Wie weit zurück crawlen

  🎯 Empfohlener Workflow:

  1. Starten Sie mit einem kleinen Zeitraum (1-2 Tage)
  2. Überwachen Sie den Status regelmäßig
  3. Prüfen Sie die Dateisystem-Ausgabe
  4. Skalieren Sie auf größere Zeiträume
  5. Aktivieren Sie Scheduling für kontinuierliches Crawling

  Das System respektiert automatisch die robots.txt Beschränkungen und implementiert Rate Limiting für ethisches Crawling.
