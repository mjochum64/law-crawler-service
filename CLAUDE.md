# Legal Document Crawler Service - Context Documentation

## Project Overview
**Type**: Containerized Spring Boot Web Service for XML Legal Document Crawling  
**Source**: https://www.rechtsprechung-im-internet.de  
**Tech Stack**: Spring Boot 3.2.2, Java 17, Maven, Apache Solr 9.4.1, Docker Compose  
**Purpose**: Downloads, indexes and validates XML documents (Rechtsprechung, Gesetze, Verwaltungsvorschriften)

## Container Architecture Summary
- **Main Application**: `de.legal.crawler.LawCrawlerServiceApplication`
- **Storage Backend**: Apache Solr collection `legal-documents` for search + XML files for archival
- **Document Storage**: `/app/data/legal-documents/` directory (Docker volume: `crawler_data`)
- **Container Network**: Docker Compose with Nginx reverse proxy, Solr, and Spring Boot app
- **Async Processing**: 8-16 thread pool for crawling operations
- **Scheduling**: Quartz-based with daily/weekly cron jobs
- **Ports**: Nginx (8888), App (8080), Solr (8983)

## Core Services & Repositories
1. **CrawlerOrchestrationService** - Central coordinator for crawling workflow
2. **SitemapCrawlerService** - Discovers and processes sitemaps with gzip support
3. **SitemapDiscoveryService** - Intelligent sitemap discovery with content validation 
4. **DocumentDownloadService** - Downloads and stores XML documents (dual storage)
5. **XmlValidationService** - Validates LegalDocML and ECLI formats
6. **ScheduledCrawlerService** - Manages periodic crawling tasks
7. **SolrDocumentRepository** - Solr-based repository with robust field conversion
8. **BulkCrawlerService** - Large-scale crawling operations (Solr-aware)

## Configuration Profiles
- **Docker Profile**: `application-docker.yml` - Container-optimized settings
- **Solr Profile**: `application-solr.yml` - Solr-specific configuration  
- **Default Profile**: `application.yml` - Base configuration
- **Active Profiles**: `docker,solr` in production container environment

## Configuration
- **Config Files**: Multiple profile-based YAML files
- **Base URL**: `https://www.rechtsprechung-im-internet.de`
- **Rate Limiting**: 2000ms between requests
- **Solr URL**: `http://solr:8983/solr/legal-documents`
- **Storage Type**: Dual (Solr index + XML files)
- **XML Validation**: Schema validation with security controls
- **Logging**: Container logs + file-based at `/app/logs/crawler.log`

## Docker Development Commands
```bash
# Full system startup (recommended)
docker-compose up -d

# Build and restart specific service
docker-compose build crawler-app && docker-compose up -d

# View logs
docker-compose logs -f crawler-app

# Reset Solr data
curl -X POST "http://localhost:8983/solr/legal-documents/update?commit=true" \
     -H "Content-Type: text/xml" \
     --data-binary "<delete><query>*:*</query></delete>"

# System teardown
docker-compose down
```

## Alternative Local Development
```bash
# Start only Solr for local development
docker-compose up -d solr

# Run app locally with Solr profile
SPRING_PROFILES_ACTIVE=solr mvn spring-boot:run

# Traditional Maven commands
mvn clean compile
mvn test
mvn clean package
```

## Context Restoration Instructions
When resuming work on this project, agents should:
1. Load this CLAUDE.md for project context
2. Start Docker environment with `docker-compose up -d`
3. Verify container health with `docker-compose ps` and `docker-compose logs`
4. Check Solr status at http://localhost:8983/solr
5. Test API endpoints at http://localhost:8080/api/crawler/status
6. Review recent git commits for latest changes

## Monitoring & Logging Infrastructure
**Complete Observability Stack**: Prometheus + Grafana + Loki  
**Metrics Endpoints**: 
- `/actuator/prometheus` - Micrometer metrics for monitoring
- `/actuator/health` - Container health checks
- `/actuator/metrics` - Spring Boot actuator metrics

**Grafana Dashboards** (Access: http://localhost:3000, admin/admin123):
- **Legal Crawler System Dashboard**: JVM, HTTP, Performance, Tomcat metrics
- **Legal Crawler Business Dashboard**: Document counts, crawling activity, search requests  
- **Legal Crawler Logs Dashboard**: Real-time log analysis with filtering

**Log Aggregation Stack**:
- **Loki** (Port 3100): Log aggregation and storage
- **Promtail**: Automatic log collection from `/app/logs/crawler.log`
- **Structured Logging**: Parsed by thread, level, logger, document_id, message

**Monitoring Services** (Docker Compose `--profile monitoring`):
- **Prometheus** (Port 9090): Metrics collection and storage
- **Grafana** (Port 3000): Visualization and dashboards
- **Loki** (Port 3100): Log aggregation
- **Promtail**: Log shipping agent

## Test & Load Generation Scripts  
- `generate_test_data.sh`: One-time comprehensive API testing (multiple endpoints)
- `simple_load_test.sh`: Continuous background load generation
- `continuous_load.sh`: Advanced load patterns with randomization

## Last Analysis Session
**Date**: 2025-08-07  
**Latest Update**: Complete monitoring and logging infrastructure implementation  
**Key Features Added**:
- ✅ Prometheus metrics with Micrometer registry integration
- ✅ Grafana dashboards for system and business monitoring
- ✅ Loki + Promtail log aggregation pipeline  
- ✅ Real-time log visualization in Grafana
- ✅ Structured log parsing with labels (level, logger, thread, document_id)
- ✅ Automated log forwarding from Spring Boot application
- ✅ Load testing scripts for continuous monitoring data generation
- ✅ Production-ready observability stack with Docker Compose profiles

**Current Status**: Full-stack production system with comprehensive monitoring, metrics, and logging