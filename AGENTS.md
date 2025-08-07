# AGENTS.md - Law Crawler Service

## Build & Test Commands
- **Build**: `mvn clean compile`
- **Test all**: `mvn test`
- **Test single**: `mvn test -Dtest=ClassName` or `mvn test -Dtest=ClassName#methodName`
- **Run app**: `mvn spring-boot:run`
- **Package**: `mvn clean package`

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