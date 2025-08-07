# Docker Setup für Legal Document Crawler mit Apache Solr

Dieses Setup stellt eine komplette Docker-basierte Umgebung für den Legal Document Crawler mit Apache Solr bereit.

## 🏗️ Architektur

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Nginx Proxy   │    │ Crawler App     │    │  Apache Solr    │
│   Port: 80      │◄──►│ Port: 8080      │◄──►│  Port: 8983     │
│   (Load Balancer│    │ (Spring Boot)   │    │  (Search Index) │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │              ┌─────────────────┐              │
         │              │   Monitoring    │              │
         └──────────────►│ Prometheus:9090 │◄─────────────┘
                        │ Grafana: 3000   │
                        └─────────────────┘
```

## 🚀 Schnellstart

### 1. Repository klonen und Setup ausführen
```bash
# Repository klonen (falls noch nicht geschehen)
git clone <your-repo>
cd law-crawler-service

# Docker-Setup ausführen
chmod +x scripts/*.sh
./scripts/setup-docker.sh
```

### 2. Services starten
```bash
# Standard-Start (Entwicklung)
./scripts/start-crawler.sh

# Mit Monitoring
./scripts/start-crawler.sh --monitoring

# Produktions-Modus
./scripts/start-crawler.sh --environment production --monitoring
```

### 3. Services stoppen
```bash
# Graceful Stop
./scripts/stop-crawler.sh

# Mit Volumes löschen (⚠️ Daten gehen verloren)
./scripts/stop-crawler.sh --volumes
```

## 📋 Verfügbare Services

| Service | URL | Beschreibung |
|---------|-----|--------------|
| **Crawler API** | http://localhost:8080/api/ | REST API für Document Crawling |
| **Health Check** | http://localhost:8080/actuator/health | Service Health Status |
| **Solr Admin** | http://localhost:8983/solr/ | Solr Admin Interface |
| **Nginx Proxy** | http://localhost/ | Load Balancer & Reverse Proxy |
| **Prometheus** | http://localhost:9090/ | Metrics Collection |
| **Grafana** | http://localhost:3000/ | Dashboards (admin/admin123) |

## 🐳 Docker Compose Services

### Core Services
- **solr**: Apache Solr 9.4.1 mit deutscher Textanalyse
- **crawler-app**: Spring Boot Anwendung
- **solr-init**: Einmalige Collection-Initialisierung
- **nginx**: Reverse Proxy mit Rate Limiting

### Optional (mit --profile monitoring)
- **prometheus**: Metrics Collection
- **grafana**: Monitoring Dashboards

## ⚙️ Konfiguration

### Environment Variables
```bash
# Solr Configuration
SOLR_URL=http://solr:8983/solr
SOLR_COLLECTION=legal-documents

# Crawler Settings
CRAWLER_STORAGE_TYPE=solr
CRAWLER_RATE_LIMIT_MS=1000

# Java Settings
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
```

### Volumes
- `solr_data`: Solr Index und Configuration
- `crawler_data`: Crawler Daten und XML Backups
- `crawler_logs`: Application Logs
- `prometheus_data`: Prometheus Metrics
- `grafana_data`: Grafana Dashboards

## 🔧 Entwicklung

### Logs anzeigen
```bash
# Alle Services
docker-compose logs -f

# Nur Crawler
docker-compose logs -f crawler-app

# Nur Solr
docker-compose logs -f solr
```

### Service Status
```bash
docker-compose ps
```

### Einzelne Services neustarten
```bash
docker-compose restart crawler-app
docker-compose restart solr
```

### In Container einsteigen
```bash
# Crawler Application
docker-compose exec crawler-app /bin/bash

# Solr
docker-compose exec solr /bin/bash
```

## 🧪 Testing

### Health Checks
```bash
# Crawler Health
curl http://localhost:8080/actuator/health

# Solr Ping
curl http://localhost:8983/solr/legal-documents/admin/ping
```

### API Tests
```bash
# Document Search
curl "http://localhost:8080/api/documents/search?q=*:*&size=5"

# Start Crawl
curl -X POST "http://localhost:8080/api/crawl/start" \
  -H "Content-Type: application/json" \
  -d '{"date":"2024-01-15","forceUpdate":false}'

# Solr Query
curl "http://localhost:8983/solr/legal-documents/select?q=*:*&rows=5"
```

## 📊 Monitoring

### Prometheus Metrics
- Application: http://localhost:8080/actuator/prometheus
- Solr: http://localhost:8983/solr/admin/metrics?wt=prometheus

### Grafana Dashboards
1. Login: http://localhost:3000/ (admin/admin123)
2. Import Dashboard: Legal Document Crawler Overview
3. Metrics:
   - HTTP Request Rates
   - Solr Query Performance
   - JVM Memory Usage
   - Document Processing Stats

## 🚨 Troubleshooting

### Common Issues

#### Port bereits belegt
```bash
# Prüfe welcher Service Port 8080 verwendet
lsof -i :8080

# Stoppe den Service oder ändere Port in docker-compose.yml
```

#### Solr Collection nicht gefunden
```bash
# Neu-Initialisierung
docker-compose up --no-deps solr-init

# Manuell erstellen
docker-compose exec solr solr create_collection -c legal-documents
```

#### Application startet nicht
```bash
# Logs prüfen
docker-compose logs crawler-app

# Rebuild erzwingen
docker-compose build --no-cache crawler-app
docker-compose up -d crawler-app
```

#### Speicher-Probleme
```bash
# Java Heap Size erhöhen
export JAVA_OPTS="-Xms1g -Xmx4g"
docker-compose restart crawler-app

# Solr Memory erhöhen
# In docker-compose.yml: SOLR_HEAP=4g
```

### Debug Mode
```bash
# Debug Logging aktivieren
docker-compose exec crawler-app bash -c 'echo "logging.level.de.legal.crawler=DEBUG" >> /app/config/application-docker.yml'
docker-compose restart crawler-app
```

## 🔄 Updates & Wartung

### Application Update
```bash
# Code ändern, dann rebuild
./mvnw clean package -DskipTests
docker-compose build crawler-app
docker-compose up -d crawler-app
```

### Solr Index Optimierung
```bash
# Manuell optimieren
curl -X POST "http://localhost:8983/solr/legal-documents/update?optimize=true"

# Automatisch täglich um 2:00 Uhr (konfiguriert in Schema)
```

### Backup erstellen
```bash
# Volumes sichern
docker run --rm -v law-crawler-service_solr_data:/data -v $(pwd)/backup:/backup alpine tar czf /backup/solr_data_$(date +%Y%m%d).tar.gz -C /data .
docker run --rm -v law-crawler-service_crawler_data:/data -v $(pwd)/backup:/backup alpine tar czf /backup/crawler_data_$(date +%Y%m%d).tar.gz -C /data .
```

## 📈 Performance Tuning

### Produktions-Optimierungen
```yaml
# docker-compose.prod.yml
services:
  crawler-app:
    environment:
      - JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
    deploy:
      resources:
        limits:
          memory: 4g
        reservations:
          memory: 2g
  
  solr:
    environment:
      - SOLR_HEAP=4g
      - SOLR_JAVA_MEM=-Xms2g -Xmx4g
```

### Solr Performance
- Index Optimization: Täglich um 2:00 Uhr
- Soft Commits: Alle 1 Sekunde
- Hard Commits: Alle 5 Sekunden
- JVM: G1GC für niedrige Latenz

## 🔐 Security

### Nginx Security Headers
- X-Frame-Options: SAMEORIGIN
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block
- Rate Limiting: 10 req/s für API

### Solr Security
- Basic Auth (optional): admin/solr123
- Zugriffsbeschränkung über Nginx
- SSL/TLS (konfigurierbar)

## 🎯 Nächste Schritte

1. **SSL/TLS Setup**: HTTPS für Produktions-Deployment
2. **Persistent Volumes**: Für Produktions-Daten
3. **Auto-Scaling**: Horizontal Pod Autoscaling
4. **Backup-Strategie**: Automatische Backups
5. **Log Aggregation**: ELK Stack Integration

---

**Viel Erfolg mit dem Solr-basierten Legal Document Crawler!** 🚀