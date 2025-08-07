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
2. Check MCP memory namespace "hive-collective" for previous findings
3. Review recent git commits for latest changes
4. Validate service status with `mvn test`

## Last Analysis Session
**Date**: 2025-08-07  
**Hive Mind Session**: swarm_1754541238628_58dzufkk2  
**Key Findings**: Complete architecture documented, context management implemented  
**Status**: Context persistence system active