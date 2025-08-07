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