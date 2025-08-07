# Legal Document Crawler Service - Context Documentation

## Project Overview
**Type**: Spring Boot Web Service for XML Legal Document Crawling  
**Source**: https://www.rechtsprechung-im-internet.de  
**Tech Stack**: Spring Boot 3.2.2, Java 17, Maven, H2 Database  
**Purpose**: Downloads and validates XML documents (Rechtsprechung, Gesetze, Verwaltungsvorschriften)

## Architecture Summary
- **Main Application**: `de.legal.crawler.LawCrawlerServiceApplication`
- **Database**: H2 file-based at `./data/legal-documents` 
- **Document Storage**: `./legal-documents/` directory
- **Async Processing**: 8-16 thread pool for crawling operations
- **Scheduling**: Quartz-based with daily/weekly cron jobs

## Core Services
1. **CrawlerOrchestrationService** - Central coordinator for crawling workflow
2. **SitemapCrawlerService** - Discovers and processes sitemaps
3. **DocumentDownloadService** - Downloads and stores XML documents  
4. **XmlValidationService** - Validates LegalDocML and ECLI formats
5. **ScheduledCrawlerService** - Manages periodic crawling tasks

## Configuration
- **Config File**: `src/main/resources/application.yml`
- **Base URL**: `https://www.rechtsprechung-im-internet.de`
- **Rate Limiting**: 2000ms between requests
- **XML Validation**: Schema validation with security controls
- **Logging**: File-based at `./logs/crawler.log`

## Development Commands
```bash
# Build project
mvn clean compile

# Run tests  
mvn test

# Start service
mvn spring-boot:run

# Package application
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