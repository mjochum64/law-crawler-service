# Legal Document Crawler Service

Ein Spring Boot Service zum Crawlen von Rechtsprechung, Gesetzen und Verwaltungsvorschriften von https://www.rechtsprechung-im-internet.de

## 🚀 Features

- **Automatisches Crawling** von XML-Dokumenten über Sitemaps
- **Strukturierte Speicherung** nach Gericht, Jahr und Monat
- **Rate Limiting** und ethisches Crawling
- **Scheduling** für automatisierte Crawling-Sessions
- **REST API** für Steuerung und Monitoring
- **H2 Database** für Metadaten-Speicherung
- **Spring Boot Actuator** für Health Checks

## 🏛️ Unterstützte Gerichte

- **BAG** - Bundesarbeitsgericht
- **BGH** - Bundesgerichtshof  
- **BSG** - Bundessozialgericht
- **BVerwG** - Bundesverwaltungsgericht

## 📋 Voraussetzungen

- Java 17+
- Maven 3.8+

## 🛠️ Installation & Start

```bash
# Repository klonen
git clone <repository-url>
cd law-crawler-service

# Abhängigkeiten installieren und starten
mvn spring-boot:run

# Alternative: JAR erstellen und ausführen
mvn clean package
java -jar target/law-crawler-service-1.0.0-SNAPSHOT.jar
```

## 🔧 Konfiguration

Konfiguration über `application.yml`:

```yaml
crawler:
  base-url: https://www.rechtsprechung-im-internet.de
  rate-limit-ms: 2000  # 2 Sekunden zwischen Requests
  storage:
    base-path: ./legal-documents
  scheduled:
    enabled: true
    days-back: 7
```

## 🌐 REST API

### Crawling starten
```bash
POST /api/crawler/crawl?date=2024-08-06&forceUpdate=false
```

### Status abfragen
```bash
GET /api/crawler/status
```

### Dokumente suchen
```bash
GET /api/crawler/search?query=arbeitsrecht
```

### Fehlgeschlagene Downloads wiederholen
```bash
POST /api/crawler/retry-failed
```

### Dokumente nach Gericht
```bash
GET /api/crawler/documents/BAG?page=0&size=20
```

## 📁 Verzeichnisstruktur

Dokumente werden strukturiert gespeichert:

```
legal-documents/
├── bag/          # Bundesarbeitsgericht
│   ├── 2024/
│   │   ├── 01/   # Januar
│   │   └── 02/   # Februar
├── bgh/          # Bundesgerichtshof
├── bsg/          # Bundessozialgericht
└── bverwg/       # Bundesverwaltungsgericht
```

## ⏰ Automatisierung

### Scheduling-Optionen

- **Täglich 6:00 Uhr**: Crawling der letzten 7 Tage
- **Sonntags 2:00 Uhr**: Vollständiger Crawl der letzten 30 Tage
- **Alle 6 Stunden**: Wiederholung fehlgeschlagener Downloads
- **Stündlich**: System Health Check

## 📊 Monitoring

### H2 Console
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/legal-documents`
- Username: `sa`

### Actuator Endpoints
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Info: http://localhost:8080/actuator/info

## 🧪 Tests

```bash
# Unit Tests ausführen
mvn test

# Integration Tests
mvn verify

# Coverage Report
mvn jacoco:report
```

## ⚖️ Rechtliche Hinweise

- **Respektiert robots.txt** der Zielwebseite
- **Rate Limiting** implementiert (2 Sekunden zwischen Requests)
- **Nur öffentlich verfügbare** Dokumente
- **Keine rechtliche Beratung** durch diesen Service

## 🔧 Entwicklung

### Architektur

- **CrawlerOrchestrationService**: Zentrale Koordination
- **SitemapCrawlerService**: Sitemap-Parsing
- **DocumentDownloadService**: XML-Download und -Speicherung  
- **ScheduledCrawlerService**: Automatisierte Ausführung
- **LegalDocument**: JPA Entity für Metadaten

### Technologie-Stack

- Spring Boot 3.2.2
- Java 17
- H2 Database
- JSoup für HTML/XML-Parsing
- Apache HttpComponents
- Spring Data JPA
- Spring Scheduling

## 📝 Logs

Logs werden gespeichert in `./logs/crawler.log`

- Rotation: 10MB pro Datei
- Historie: 30 Dateien
- Level: INFO (konfigurierbar)

## 🚨 Troubleshooting

### Häufige Probleme

1. **403 Forbidden**: robots.txt verbietet Crawling - kontaktiere kompetenzzentrum-ris@bfj.bund.de
2. **OutOfMemory**: JVM Heap Size erhöhen (`-Xmx2g`)
3. **Speicherplatz**: Regelmäßige Bereinigung alter Dateien

### Logs prüfen
```bash
tail -f logs/crawler.log
```

## 🤝 Beitragen

1. Fork des Repositories
2. Feature Branch erstellen
3. Tests hinzufügen
4. Pull Request erstellen

## 📄 Lizenz

Dieses Projekt steht unter der [MIT Lizenz](LICENSE).