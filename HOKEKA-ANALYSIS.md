# HOKEKA Agent Analysis Log

## Analysis Date
2026-03-12

## Repository
https://github.com/kevshake/Fraud_Detector

## Analysis Status
✅ Backend Analysis Complete  
⏳ Frontend Analysis Pending  
⏳ Deployment Architecture Pending

---

## Backend Analysis

### Overview
The HOKEKA Fraud Detector backend is a **Spring Boot 3.2.0** application built with **Java 17**, designed as a high-throughput, multi-tenant AML (Anti-Money Laundering) and fraud detection platform.

### Technology Stack

| Component | Version/Technology |
|-----------|-------------------|
| Framework | Spring Boot 3.2.0 |
| Language | Java 17 |
| Build Tool | Maven 3.9+ |
| Primary Database | PostgreSQL 15+ |
| Cache | Aerospike 6.x |
| Graph Database | Neo4j 5.x (optional) |
| Security | Spring Security 6.x |
| Message Queue | Apache Kafka |
| Rule Engine | Drools 8.44.0.Final |
| ML/DL | DL4J 1.0.0-M2.1 |
| Monitoring | Prometheus + Grafana |
| API Docs | SpringDoc OpenAPI 2.3.0 |

### Key Dependencies

**Core Spring Dependencies:**
- `spring-boot-starter-web` - REST API
- `spring-boot-starter-webflux` - WebClient for async HTTP
- `spring-boot-starter-data-jpa` - Data access
- `spring-boot-starter-security` - Authentication/authorization
- `spring-boot-starter-actuator` - Health checks and metrics
- `spring-boot-starter-validation` - Input validation

**Database & Caching:**
- PostgreSQL JDBC driver
- Aerospike client 9.2.0 (JDK 8-17 compatible)
- Neo4j Spring Data
- HikariCP connection pooling
- Hibernate Envers (auditing)

**Additional Libraries:**
- Flyway 11.14.1 (database migrations)
- Kafka (event streaming)
- Resilience4j (circuit breaker)
- Caffeine (in-memory caching)
- MapStruct (DTO mapping)
- Lombok (boilerplate reduction)
- OpenPDF (report generation)

### Project Structure

```
BACKEND/src/main/java/com/posgateway/aml/
├── AmlFraudDetectorApplication.java    # Main entry point
├── config/                             # Configuration classes
│   ├── SecurityConfig.java             # Spring Security setup
│   ├── PspLoggingFilter.java           # PSP-based logging (MDC)
│   ├── AerospikeConfig.java
│   └── ...
├── controller/                         # REST API endpoints
│   ├── TransactionController.java      # Transaction ingestion
│   ├── AlertController.java            # Alert management
│   ├── ComplianceCaseController.java   # Case management
│   ├── UserController.java             # User management
│   ├── auth/                           # Authentication endpoints
│   ├── psp/                            # PSP management
│   └── ...
├── service/                            # Business logic (31 sub-packages)
│   ├── risk/                           # Risk scoring
│   ├── compliance/                     # Compliance services
│   ├── psp/                            # PSP services
│   ├── sanctions/                      # Sanctions screening
│   ├── case_management/                # Case workflows
│   └── ...
├── entity/                             # JPA entities
│   ├── psp/Psp.java                    # PSP entity
│   ├── User.java                       # User entity
│   ├── TransactionEntity.java
│   └── ...
├── repository/                         # Data access layer
├── dto/                                # Data transfer objects
├── exception/                          # Custom exceptions
└── mapper/                             # Entity-DTO mappers
```

### Multi-Tenancy Implementation

**Architecture: Shared Database with PSP ID Column Isolation**

The system implements **strict PSP (Payment Service Provider) isolation** using a shared database approach where all tenant data resides in the same tables with a `psp_id` column for filtering.

**Key Implementation Details:**

1. **PSP Entity** (`entity/psp/Psp.java`):
   - Primary key: `psp_id` (Long)
   - PSP ID 0 = Super Admin (Platform Administrator)
   - PSP ID > 0 = Individual PSP/Bank tenants
   - Contains theming, billing, and configuration per tenant

2. **User Entity** (`entity/User.java`):
   - Every user belongs to a PSP via `@ManyToOne` relationship
   - Users with PSP ID 0 are Platform Administrators
   - Users with PSP ID > 0 are PSP-specific users

3. **Transaction Entity** (`entity/TransactionEntity.java`):
   - Contains `psp_id` column for tenant isolation
   - All queries filtered by PSP ID

4. **PspIsolationService** (`service/security/PspIsolationService.java`):
   - Centralized service for enforcing data isolation
   - Methods:
     - `getCurrentUserPspId()` - Get current user's PSP ID
     - `isPlatformAdministrator()` - Check if Super Admin
     - `validatePspAccess(targetPspId)` - Validate cross-PSP access
     - `sanitizePspId(requestedPspId)` - Sanitize PSP ID from requests
     - `validateCaseAccess()`, `validateTransactionAccess()`, `validateMerchantAccess()`

**Role-Based Access Control:**

| Role | Description | PSP ID |
|------|-------------|--------|
| `ADMIN` / `PLATFORM_ADMIN` / `MLRO` | Platform Administrator | 0 |
| `PSP_ADMIN` | PSP Administrator | > 0 |
| `PSP_ANALYST` | PSP Analyst | > 0 |
| `COMPLIANCE_OFFICER` | Compliance Officer | Any |
| `ANALYST` | Analyst | Any |

**PSP Isolation in Controllers:**

```java
// Example from TransactionController.java
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'ANALYST', 'PSP_ADMIN', 'PSP_ANALYST', 'VIEWER')")
public ResponseEntity<Page<TransactionEntity>> getAllTransactions(
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "25") int size,
        @RequestParam(required = false) Long pspId) {
    
    // Enforce PSP isolation - sanitize PSP ID parameter
    Long sanitizedPspId = pspIsolationService.sanitizePspId(pspId);
    
    // Build Specification for filtering
    Specification<TransactionEntity> spec = Specification.where(null);
    
    // PSP Isolation Logic
    if (sanitizedPspId != null) {
        spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), sanitizedPspId));
    }
    // ...
}
```

### Main Application Entry Point

**File:** `AmlFraudDetectorApplication.java`

```java
@SpringBootApplication(exclude = {RedisRepositoriesAutoConfiguration.class})
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.posgateway.aml.repository", ...)
@EnableNeo4jRepositories(basePackages = "com.posgateway.aml.repository.graph")
public class AmlFraudDetectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AmlFraudDetectorApplication.class, args);
    }
}
```

**Server Configuration:**
- Default Port: 2637
- Context Path: `/api/v1`
- Session Timeout: 30 minutes
- Session Fixation Protection: Enabled (`migrateSession()`)

### API Endpoints

**Authentication Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/login` | User login (session-based) |
| POST | `/auth/logout` | User logout |
| GET | `/auth/me` | Get current user details |
| GET | `/auth/csrf` | Get CSRF token |

**Transaction Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/transactions/ingest` | Ingest single transaction |
| POST | `/transactions/ingest/batch` | Batch transaction ingestion |
| GET | `/transactions` | List transactions (paginated) |
| GET | `/transactions/{id}` | Get transaction by ID |
| GET | `/transactions/health` | Health check |

**Alert Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/alerts` | List alerts (PSP-filtered) |
| GET | `/alerts/{id}` | Get alert details |
| PUT | `/alerts/{id}/disposition` | Update alert disposition |
| GET | `/alerts/disposition-stats` | Get disposition statistics |

**Case Management Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/compliance/cases` | List compliance cases |
| GET | `/compliance/cases/{id}` | Get case details |
| GET | `/compliance/cases/stats` | Get case statistics |
| PUT | `/compliance/cases/{id}` | Update case |

**User Management Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users` | List users |
| GET | `/users/{id}` | Get user details |
| POST | `/users` | Create user |
| PUT | `/users/{id}` | Update user |
| DELETE | `/users/{id}` | Delete user |

**PSP Management Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/psps` | List PSPs |
| POST | `/psps/register` | Register new PSP |
| GET | `/psps/{id}` | Get PSP details |
| PUT | `/psps/{id}` | Update PSP |

### Database Configuration

**Primary Database: PostgreSQL**

Configuration in `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aml_fraud_db
spring.datasource.username=postgres
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
```

**Connection Pooling (HikariCP):**
- Max connections: Configurable
- Connection timeout: 20 seconds

**Database Migrations (Flyway):**
- Location: `src/main/resources/db/migration/`
- 100+ migration files (V1 through V107)
- Includes: Schema creation, indexes, sample data, fixes

**Key Tables:**
- `psps` - Payment Service Providers (tenants)
- `platform_users` - User accounts
- `transactions` - Transaction records
- `alerts` - Fraud/AML alerts
- `compliance_cases` - Investigation cases
- `merchants` - Merchant accounts
- `sanctions_entities` - Sanctions screening data
- `audit_logs` - Audit trail

### Authentication & Authorization

**Authentication Method:** Session-based (stateful)

**Security Features:**
- Session-based authentication with JSESSIONID cookie
- Session timeout: 30 minutes
- Session fixation protection: `migrateSession()`
- CSRF protection: Disabled for REST API (`csrf.disable()`)
- Password encoding: BCrypt (via Spring Security)

**Authorization:**
- Method-level security with `@PreAuthorize`
- URL-based security in `SecurityConfig.java`
- Role-based access control

**Security Configuration Highlights:**
```java
http
    .csrf(csrf -> csrf.disable())  // REST API uses session auth
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/login", "/api/v1/auth/login").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/v1/cases/**").hasAnyRole("COMPLIANCE_OFFICER", "ADMIN")
        .anyRequest().authenticated()
    )
    .sessionManagement(session -> {
        session.maximumSessions(1)  // One session per user
               .maxSessionsPreventsLogin(false);
        session.sessionFixation().migrateSession();  // Fixation protection
    });
```

### Build & Deployment Requirements

**Build Requirements:**
- Java 17 or higher
- Maven 3.9+
- PostgreSQL 15+ (running)

**Build Commands:**
```bash
# Compile
mvn clean compile

# Package
mvn clean package

# Run tests
mvn test

# Run application
mvn spring-boot:run
```

**Runtime Dependencies:**
1. **PostgreSQL** - Primary database
2. **Aerospike** (optional) - High-performance caching
3. **Neo4j** (optional) - Graph analytics
4. **Kafka** (optional) - Event streaming
5. **ML Scoring Service** (optional) - External Python service on port 8000

**Environment Variables:**
| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Application port | 2637 |
| `DATABASE_URL` | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/aml_fraud_db |
| `DATABASE_USERNAME` | Database username | postgres |
| `DATABASE_PASSWORD` | Database password | - |
| `AEROSPIKE_ENABLED` | Enable Aerospike | true |
| `AEROSPIKE_HOSTS` | Aerospike hosts | localhost:3000 |
| `KAFKA_ENABLED` | Enable Kafka | true |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | localhost:9092 |
| `NEO4J_ENABLED` | Enable Neo4j | false |

**Docker Support:**
- Docker Compose configuration available (implied from docs)
- Nginx reverse proxy for production

### High-Throughput Optimizations

The backend is optimized for ultra-high throughput (30,000+ TPS):

1. **Tomcat Configuration:**
   - Min spare threads: 200
   - Max threads: 1000
   - Max connections: 10,000
   - Accept count: 5,000

2. **Async Processing:**
   - `@EnableAsync` for asynchronous operations
   - `AsyncFraudDetectionOrchestrator` for non-blocking fraud detection
   - `HighConcurrencyFraudOrchestrator` for ultra-high throughput

3. **Caching Strategy:**
   - Aerospike for distributed caching
   - Caffeine for in-memory caching
   - Request buffering for burst handling

4. **Circuit Breaker:**
   - Resilience4j for external service resilience

### Monitoring & Observability

**Actuator Endpoints:**
- `/actuator/health` - Health status
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics

**Prometheus Metrics:**
- HTTP request latency
- JVM memory usage
- Database connection pool stats
- Custom business metrics

**API Documentation:**
- Swagger UI: `http://localhost:2637/swagger-ui.html`
- OpenAPI spec: `/api-docs`

### Key Findings Summary

1. **Multi-Tenancy:** Shared database with PSP ID column isolation - NOT schema-per-tenant or database-per-tenant
2. **Java/Spring Versions:** Java 17, Spring Boot 3.2.0
3. **Authentication:** Session-based (stateful), not JWT
4. **Database:** PostgreSQL with Flyway migrations
5. **Caching:** Aerospike for high-performance caching
6. **Throughput:** Optimized for 30K+ TPS with async processing
7. **Security:** Role-based access with strict PSP isolation
8. **Deployment:** Traditional WAR/JAR deployment with Docker support

---

## Frontend Analysis
*Pending analysis by Frontend Developer agent*

## Deployment Architecture
*Pending analysis by Technical Implementer agent*
