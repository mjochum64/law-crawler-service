# Legal Document Crawler Service

Ein containerisierter Spring Boot Service zum Crawlen von Rechtsprechung, Gesetzen und Verwaltungsvorschriften von https://www.rechtsprechung-im-internet.de

## 🚀 Features

- **🐳 Docker-basiertes Setup** mit vollständiger Container-Orchestrierung
- **🔍 Apache Solr Integration** für performante Volltextsuche
- **📁 Duale Speicherung** - XML-Dateien + Solr-Index für beste Flexibilität  
- **⚡ Intelligente Sitemap-Discovery** mit Gzip-Unterstützung
- **🚦 Rate Limiting** und ethisches Crawling
- **📅 Scheduling** für automatisierte Crawling-Sessions
- **🌐 REST API** für Steuerung und Monitoring
- **📊 Nginx Reverse Proxy** mit Load Balancing
- **🔧 Comprehensive Health Checks** und Monitoring

## 🏛️ Unterstützte Gerichte

- **BAG** - Bundesarbeitsgericht
- **BGH** - Bundesgerichtshof  
- **BSG** - Bundessozialgericht
- **BVerwG** - Bundesverwaltungsgericht

## 📋 Voraussetzungen

- **Docker & Docker Compose** (empfohlen)
- Alternativ: Java 17+ und Maven 3.8+

## 🚀 Quick Start mit Docker

```bash
# Repository klonen
git clone <repository-url>
cd law-crawler-service

# Komplettes System starten (Solr + Crawler + Nginx)
docker-compose up -d

# Logs verfolgen
docker-compose logs -f

# System stoppen
docker-compose down
```

**🎯 Nach dem Start verfügbar:**
- **API:** http://localhost:8080
- **Nginx Gateway:** http://localhost:8888  
- **Solr Admin:** http://localhost:8983/solr
- **Grafana Dashboards:** http://localhost:3000 (admin/admin123)
- **Prometheus Metrics:** http://localhost:9090

## 🏗️ Architektur

```
                    ┌─────────────────┐    ┌─────────────────┐
                    │   Prometheus    │    │     Grafana     │
                    │   Metrics       │◄──►│   Dashboards    │
                    │   Port 9090     │    │   Port 3000     │
                    └─────────────────┘    └─────────────────┘
                              ▲                      ▲
                              │                      │
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Nginx       │    │  Spring Boot    │    │   Apache Solr   │
│  Reverse Proxy  │◄──►│   Crawler App   │◄──►│   Search Index  │
│   Port 8888     │    │    Port 8080    │    │   Port 8983     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │  XML File Store │    │  Loki + Promtail│
                       │ /app/data/legal │    │   Log Pipeline  │
                       └─────────────────┘    └─────────────────┘
```

### 🔍 Monitoring Stack
- **Prometheus**: Metrics collection (JVM, HTTP, Tomcat, Business metrics)
- **Grafana**: 3 Dashboards - System, Business, Logs
- **Loki + Promtail**: Log aggregation and real-time analysis
- **Health Checks**: Application and container-level monitoring

## 🔧 Konfiguration

### Docker Profile (Standard)
```yaml
# application-docker.yml
crawler:
  storage:
    type: solr
    base-path: /app/data/legal-documents
  solr:
    url: http://solr:8983/solr
    collection: legal-documents
```

### Umgebungsvariablen
```bash
SPRING_PROFILES_ACTIVE=docker,solr
SOLR_URL=http://solr:8983/solr
SOLR_COLLECTION=legal-documents
JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC
```

## 🌐 REST API

### 🕷️ Crawling starten
```bash
# Einzeldatum crawlen
POST http://localhost:8080/api/crawler/crawl?date=2025-01-15

# Mit Force-Update
POST http://localhost:8080/api/crawler/crawl?date=2025-01-15&forceUpdate=true
```

### 📊 Status abfragen
```bash
GET http://localhost:8080/api/crawler/status
# Response: {"totalDocuments":1247,"downloadedDocuments":1247,"pendingDocuments":0,...}
```

### 🔍 Dokumente suchen
```bash
GET http://localhost:8080/api/crawler/search?query=arbeitsrecht
```

### 🏛️ Dokumente nach Gericht
```bash
GET http://localhost:8080/api/crawler/documents/BAG?page=0&size=20
```

### 🧪 Sitemap-Optimierungen testen
```bash
GET http://localhost:8080/api/crawler/test/sitemap-optimizations
# Response: {"summary":"Tested 10 dates: 10 exist, 0 gzipped, 10 with content",...}
```

### 🔄 Fehlgeschlagene Downloads wiederholen
```bash
POST http://localhost:8080/api/crawler/retry-failed
```

## 📁 Speicherstruktur

### Container-Volumes
```bash
# XML-Dateien (persistent)
crawler_data:/app/data/legal-documents/

# Log-Dateien (persistent)  
crawler_logs:/app/logs/

# Solr-Daten (persistent)
solr_data:/var/solr/data/
```

### Dateiorganisation
```
/app/data/legal-documents/
├── bag/          # Bundesarbeitsgericht
│   ├── 2025/
│   │   ├── 01/   # Januar
│   │   └── 02/   # Februar  
├── bgh/          # Bundesgerichtshof
├── bsg/          # Bundessozialgericht
└── bverwg/       # Bundesverwaltungsgericht
```

## 🔄 Container-Management

### Status prüfen
```bash
docker-compose ps
docker-compose logs crawler-app
docker-compose logs solr
```

### Einzelne Services neustarten
```bash
docker-compose restart crawler-app
docker-compose restart solr
docker-compose restart nginx
```

### Solr-Daten zurücksetzen
```bash
# Alle Dokumente aus Solr löschen
curl -X POST "http://localhost:8983/solr/legal-documents/update?commit=true" \
     -H "Content-Type: text/xml" \
     --data-binary "<delete><query>*:*</query></delete>"
```

### Kompletter Reset
```bash
# Alle Container und Volumes löschen
docker-compose down -v

# Neu starten  
docker-compose up -d
```

## ⏰ Automatisierung

### Aktive Cron-Jobs
- **Täglich 6:00 Uhr**: Crawling der letzten 7 Tage
- **Sonntags 2:00 Uhr**: Vollständiger Crawl der letzten 30 Tage
- **Alle 6 Stunden**: Wiederholung fehlgeschlagener Downloads
- **Stündlich**: System Health Check

## 📊 Monitoring & Health Checks

### Container Health Checks
```bash
# Crawler App Health
curl http://localhost:8080/actuator/health

# Solr Health
curl http://localhost:8983/solr/legal-documents/admin/ping

# Nginx Status
curl http://localhost:8888
```

### Actuator Endpoints
- **Health:** http://localhost:8080/actuator/health
- **Metrics:** http://localhost:8080/actuator/metrics  
- **Info:** http://localhost:8080/actuator/info
- **Solr Stats:** http://localhost:8080/actuator/solr

### Solr Admin UI
- **URL:** http://localhost:8983/solr
- **Collection:** legal-documents
- **Query Console:** http://localhost:8983/solr/#/legal-documents/query

## 🧪 Entwicklung & Tests

### Lokale Entwicklung
```bash
# Nur Solr starten für lokale Entwicklung
docker-compose up -d solr

# App lokal starten
SPRING_PROFILES_ACTIVE=solr mvn spring-boot:run
```

### Tests ausführen
```bash
# Unit Tests
mvn test

# Mit Integration Tests
mvn verify

# Coverage Report
mvn jacoco:report
```

### Build ohne Docker
```bash
mvn clean package
java -jar target/law-crawler-service-1.0.0-SNAPSHOT.jar
```

## 🔧 Technologie-Stack

### 🏗️ Backend
- **Spring Boot 3.2.2** - Application Framework
- **Java 17** - Runtime Environment  
- **Apache Solr 9.4.1** - Search Engine
- **Spring Data** - Data Access Layer
- **Quartz Scheduler** - Cron Jobs

### 🐳 Infrastructure  
- **Docker & Docker Compose** - Containerization
- **Nginx** - Reverse Proxy & Load Balancer
- **Eclipse Temurin 17** - JVM Runtime

### 📦 Libraries
- **SolrJ** - Solr Java Client
- **Jetty HTTP Client** - HTTP Communications
- **JSoup** - HTML/XML Parsing
- **Apache HttpComponents** - HTTP Client

## 🚨 Troubleshooting

### 🔍 Häufige Probleme

#### 1. Container starten nicht
```bash
# Logs prüfen
docker-compose logs

# Ports prüfen
netstat -tulpn | grep -E ':(8080|8983|8888)'

# Volumes zurücksetzen
docker-compose down -v && docker-compose up -d
```

#### 2. Solr Connection Failed
```bash
# Solr Health prüfen
curl http://localhost:8983/solr/admin/ping

# Container Networking prüfen
docker-compose exec crawler-app ping solr
```

#### 3. ClassCastException in Logs
```bash
# Fixed in current version - update to latest image
docker-compose build crawler-app
docker-compose up -d
```

#### 4. Jetty Client Errors
```bash
# Dependencies werden automatisch installiert
# Bei Problemen: Image neu bauen
docker-compose build --no-cache crawler-app
```

### 📋 Debug-Befehle
```bash
# Container Shell
docker-compose exec crawler-app bash
docker-compose exec solr bash

# Live-Logs
docker-compose logs -f crawler-app

# Resource-Verbrauch
docker stats
```

## ⚖️ Rechtliche Hinweise

- **✅ Respektiert robots.txt** der Zielwebseite
- **🚦 Rate Limiting** implementiert (2 Sekunden zwischen Requests)
- **🌐 Nur öffentlich verfügbare** Dokumente
- **⚠️ Keine rechtliche Beratung** durch diesen Service

## 🤝 Beitragen

1. **Fork** des Repositories erstellen
2. **Feature Branch** erstellen (`git checkout -b feature/amazing-feature`)
3. **Tests** hinzufügen und prüfen (`mvn test`)
4. **Docker Build** testen (`docker-compose build`)
5. **Pull Request** erstellen

## 📄 Lizenz

Dieses Projekt steht unter der [MIT Lizenz](LICENSE).

---

**🚀 Powered by Docker, Spring Boot & Apache Solr**