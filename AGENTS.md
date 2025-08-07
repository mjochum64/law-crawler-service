# AGENTS.md - Law Crawler Service

## 🚀 QUICK CONTEXT RESTORATION
**For AI agents resuming work on this project:**
1. **Architecture**: Spring Boot 3.2.2 + Java 17 + Maven
2. **Purpose**: Crawls legal documents from rechtsprechung-im-internet.de (German legal portal)
3. **Key Features**: XML validation (LegalDocML.de), ECLI validation, security sanitization, scheduled crawling
4. **Storage**: H2 database (./data/), XML files (./legal-documents/)
5. **Main API**: http://localhost:8080/api/crawler

## 🏗️ SYSTEM ARCHITECTURE OVERVIEW
- **Controllers**: CrawlerController (REST API), XmlValidationController (validation API)
- **Services**: SitemapCrawlerService, XmlValidationService, BulkCrawlerService, DocumentDownloadService
- **Models**: LegalDocument (JPA entity), BulkCrawlProgress (tracking)
- **Validators**: EcliValidator (European Case Law IDs), LegalDocMLValidator (German legal XML standard)
- **Security**: XmlSanitizer (XXE/XML bomb protection)

## 🔧 Build & Test Commands
- **Build**: `mvn clean compile`
- **Test all**: `mvn test`
- **Test single**: `mvn test -Dtest=ClassName` or `mvn test -Dtest=ClassName#methodName`
- **Run app**: `mvn spring-boot:run`
- **Package**: `mvn clean package`
- **H2 Console**: http://localhost:8080/h2-console (sa/blank password)

## Code Style Guidelines
- **Java 17** with Spring Boot 3.2.2
- **Package structure**: `de.legal.crawler.{controller,service,model,repository,validator,util,exception}`
- **Annotations**: Use `@Autowired` for dependency injection, `@Service/@Controller/@Repository` for components
- **Validation**: Use Jakarta validation (`@NotNull`, `@NotBlank`) on entities and DTOs
- **Naming**: CamelCase classes, camelCase methods/fields, UPPER_SNAKE_CASE constants
- **Imports**: Group by: java.*, javax.*, org.springframework.*, other third-party, project packages
- **Error handling**: Use custom exceptions in `exception` package, proper HTTP status codes
- **Logging**: Use SLF4J with `LoggerFactory.getLogger(ClassName.class)`
- **Database**: JPA entities with `@Entity`, `@Table`, `@Id`, `@GeneratedValue(IDENTITY)`
- **REST**: Use `@RestController`, `@RequestMapping`, proper HTTP methods and ResponseEntity
- **Config**: Use `@Value` for properties, YAML configuration in application.yml
- **Async**: Use `@Async` and CompletableFuture for concurrent operations
- **Testing**: Use Spring Boot Test with `@SpringBootTest`, `@Test`, TestContainers for integration tests

## 📊 KEY CONFIGURATION (application.yml)
- **Database**: H2 file-based at ./data/legal-documents
- **Crawler**: Base URL https://www.rechtsprechung-im-internet.de, 2s rate limit
- **Storage**: ./legal-documents directory for XML files  
- **Scheduling**: Daily 6AM, Weekly Sunday 2AM, Retry every 6h
- **Security**: Max 10MB XML, sanitization enabled, external entities blocked
- **Validation**: Schema, LegalDocML.de, ECLI validation enabled

## 🎯 IMPORTANT FILE LOCATIONS
- **Main App**: `src/main/java/de/legal/crawler/LawCrawlerServiceApplication.java`
- **API Controllers**: `src/main/java/de/legal/crawler/controller/`
- **Business Services**: `src/main/java/de/legal/crawler/service/`
- **Data Models**: `src/main/java/de/legal/crawler/model/LegalDocument.java`
- **XML Validation**: `src/main/java/de/legal/crawler/validator/`
- **Configuration**: `src/main/resources/application.yml`
- **Schemas**: `src/main/resources/schemas/simple-legal-document.xsd`

## 🧠 HIVE MIND CONTEXT RESTORATION
**To restore a Hive Mind session for this project:**

```bash
# 1. Initialize Claude Flow Hive Mind
npx claude-flow swarm_init --topology hierarchical

# 2. Spawn specialized agents
npx claude-flow agent_spawn --type researcher --capabilities "legal-documents,xml-analysis"
npx claude-flow agent_spawn --type coder --capabilities "spring-boot,java17,maven"  
npx claude-flow agent_spawn --type analyst --capabilities "architecture,performance"
npx claude-flow agent_spawn --type tester --capabilities "junit,integration-testing"

# 3. Load context from memory
npx claude-flow memory_usage --action retrieve --namespace hive --key architecture_overview
npx claude-flow memory_usage --action retrieve --namespace hive --key service_components
npx claude-flow memory_usage --action retrieve --namespace hive --key xml_processing
```

## 🔍 QUICK DEVELOPMENT WORKFLOW
1. **Start app**: `mvn spring-boot:run` (port 8080)
2. **Test crawl**: `curl -X POST "http://localhost:8080/api/crawler/crawl?date=2025-01-01"`
3. **Check status**: `curl http://localhost:8080/api/crawler/status`
4. **View logs**: `tail -f logs/crawler.log`  
5. **Database**: Open http://localhost:8080/h2-console
6. **Health check**: `curl http://localhost:8080/actuator/health`