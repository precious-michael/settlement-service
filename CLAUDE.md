# Project Overview

This is a Spring Boot backend providing REST APIs for a bank transaction reconciliation system. The service matches bank statements against settlement reports to identify discrepancies in bundled transactions.

The system follows layered architecture: Controller → Service → Repository → Database.

---

## Business Domain

**Core Function:** Reconcile bank statements with settlement reports using formula-based matching.

**Key Flows:**
1. Upload bank statement (Excel/CSV) → parse → create Transactions → auto-classify by ProductType
2. Upload settlement report (CSV) for a Transaction → parse → create SettlementTransactions
3. Run reconciliation → match SettlementTransactions to InternalRecords using formulas → flag discrepancies
4. Query transactions, discrepancies, reconciliation status

**Entities:**
- `Account` - Bank account being reconciled
- `BankStatement` - Uploaded statement file containing transactions
- `Transaction` - One line from a bank statement
- `SettlementReport` - Uploaded breakdown of one transaction
- `SettlementTransaction` - One line from a settlement report
- `InternalRecord` - Core banking system record (source of truth)
- `Discrepancy` - Mismatch found during reconciliation
- `ClassificationRule` - Pattern to auto-tag transactions
- `ReconciliationFormula` - Template for matching logic

---

## Tech Stack

- **Java 17**
- **Spring Boot 4.1.0** (Spring 7.x)
- **Spring Web** - REST APIs
- **Spring Data JPA** - Data access
- **Spring Security** - JWT authentication
- **Hibernate** - ORM
- **MySQL 8.0** - Primary database
- **Flyway** - Database migrations
- **Maven 3.9+** - Build tool
- **Apache POI** - Excel parsing
- **OpenCSV** - CSV parsing
- **JUnit 5 + Mockito** - Testing
- **Testcontainers** - Integration tests
- **AssertJ** - Fluent assertions
- **Lombok** - Boilerplate reduction

---

## Project Structure

```
src/main/java/org/settlementservice/settlementservice/
├── async/                    # Background task processors (file parsing, reconciliation)
│   ├── BankStatementUploadTask.java
│   ├── SettlementReportUploadTask.java
│   └── ClassificationMatcher.java
├── config/                   # Spring configuration
│   ├── AsyncConfig.java      # Thread pool configuration
│   ├── SecurityConfig.java   # JWT + auth setup
│   └── AdminUserSeeder.java  # Seed default admin user
├── controllers/              # REST endpoints (thin, no business logic)
│   ├── AuthController.java
│   ├── AccountController.java
│   ├── BankStatementController.java
│   ├── SettlementReportController.java
│   ├── TransactionController.java
│   ├── DiscrepancyController.java
│   └── InternalRecordController.java
├── demo/                     # Demo data generator
│   └── services/DemoFileGeneratorServiceImpl.java
├── dtos/                     # Request/response DTOs
│   ├── request/
│   └── response/
├── enums/                    # Enumerations
│   ├── BatchStatus.java
│   ├── TransactionStatus.java
│   ├── ReconciliationStatus.java
│   └── ProductType.java
├── exceptions/               # Custom exceptions
│   └── handler/GlobalExceptionHandler.java
├── models/                   # JPA entities
│   ├── Account.java
│   ├── BankStatement.java
│   ├── Transaction.java
│   ├── SettlementReport.java
│   ├── SettlementTransaction.java
│   ├── InternalRecord.java
│   ├── Discrepancy.java
│   └── User.java
├── parsers/                  # File parsers
│   ├── CsvStatementFileParser.java
│   └── ExcelStatementFileParser.java
├── reconciliation/           # Reconciliation engine
│   ├── services/ReconciliationEngine.java
│   └── services/impl/ReconciliationEngineImpl.java
├── repositories/             # Spring Data JPA repositories
│   ├── AccountRepository.java
│   ├── BankStatementRepository.java
│   ├── TransactionRepository.java
│   └── DiscrepancyRepository.java
├── security/                 # JWT + user details
│   ├── JwtService.java
│   └── UserDetailsServiceImpl.java
└── services/                 # Business logic
    ├── AccountService.java
    ├── BankStatementService.java
    ├── SettlementReportService.java
    └── DiscrepancyService.java

src/main/resources/
├── db/migration/             # Flyway migrations (V1__*.sql)
│   └── V01__initial_schema.sql ... V26__*.sql
└── application.yml           # Spring configuration
```

---

## Development Commands

### Build & Run

```bash
# Build (skip tests)
./mvnw clean install -DskipTests

# Run locally
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring.profiles.active=local

# Build Docker image
docker build -t settlement-service .

# Run with Docker Compose
docker-compose up -d
```

### Testing

```bash
# All tests
./mvnw test

# Controller integration tests only
./mvnw test -Dtest="*ControllerTest"

# Single test class
./mvnw test -Dtest=ReconciliationControllerTest

# Single test method
./mvnw test -Dtest=ReconciliationControllerTest#run_withAuthentication_returns200

# Skip Docker pre-start (if containers already running)
./mvnw test -DskipDockerRun=True

# Integration tests (requires Docker)
./mvnw verify

# Use local test DB instead of Docker
./mvnw test -Dspring.profiles.active=test

```

### Code Quality

```bash
# Check code formatting
./mvnw spotless:check

# Apply code formatting
./mvnw spotless:apply

# Run all checks before commit
./mvnw clean verify spotless:check
```

### Database

```bash
# Flyway info (check migration status)
./mvnw flyway:info

# Flyway migrate (apply pending migrations)
./mvnw flyway:migrate

# Connect to MySQL (Docker)
docker exec -it settlement-service-mysql mysql -u settlement -psettlement settlement_service
```

---

## Database Rules

### Migrations (Flyway)

1. **NEVER modify existing migrations**
   - Migrations are immutable once applied
   - Create a new migration for schema changes
   - Migration files: `V{number}__{description}.sql`

2. **Migration naming convention**
   ```
   V26__add_discrepancy_details.sql
   V27__create_reconciliation_formulas_table.sql
   ```

3. **Entity changes MUST match migrations**
   - After creating migration, update JPA entity
   - Add/remove fields in both places
   - Keep data types consistent (BigDecimal ↔ DECIMAL, LocalDate ↔ DATE)

4. **Migration checklist**
   - [ ] Create migration file in `src/main/resources/db/migration/`
   - [ ] Test migration locally (`./mvnw flyway:migrate`)
   - [ ] Update entity class
   - [ ] Update DTOs if needed
   - [ ] Update tests
   - [ ] Verify migration runs in Docker

### JPA/Hibernate

1. **Entities must reflect DB schema exactly**
   - Column names match via `@Column(name = "snake_case")`
   - Nullable columns: `@Column(nullable = false)`
   - Use `@Enumerated(EnumType.STRING)` not ordinal

2. **Avoid bidirectional relationships unless necessary**
   - Prefer `@ManyToOne` without inverse `@OneToMany`
   - Bidirectional = cache invalidation complexity

3. **Prefer LAZY fetching**
   - `@ManyToOne(fetch = FetchType.LAZY)`
   - Eagerly fetch only when always needed

4. **Use BaseEntity for common fields**
   - All entities extend BaseEntity (id, createdAt, updatedAt)
   - Don't duplicate timestamp fields

---

## Coding Rules

### Controller Layer

1. **Controllers must be thin**
   - No business logic
   - Call service methods
   - Map DTOs to responses
   - Handle HTTP concerns only (status codes, headers)

2. **Always return `ResponseEntity<SettlementServiceResponse<T>>`**
   - Standard response wrapper
   - Includes `success`, `message`, `data`
   - Example:
     ```java
     return ResponseEntity.ok(
         SettlementServiceResponse.success("Upload successful", uploadDto)
     );
     ```

3. **Use validation annotations**
   - `@Valid` on request bodies
   - `@NotNull`, `@NotBlank` on DTO fields
   - Let Spring handle 400 responses

4. **Use proper HTTP methods**
   - GET = retrieve
   - POST = create
   - PUT = full update
   - PATCH = partial update
   - DELETE = remove

5. **REST conventions**
   - Plural resource names: `/api/accounts`, `/api/transactions`
   - Sub-resources: `/api/bank-statements/{id}/transactions`
   - Query params for filters: `?status=UNRESOLVED&page=0`

### Service Layer

1. **Business logic lives here**
   - Orchestrate repositories
   - Apply business rules
   - Handle transactions
   - Throw domain exceptions

2. **Services must be transactional**
   - Use `@Transactional` on public methods
   - Default: read-write transaction
   - Read-only: `@Transactional(readOnly = true)`

3. **Prefer constructor injection**
   - Use `@RequiredArgsConstructor` (Lombok)
   - No `@Autowired` field injection
   - Easier to test (pass mocks in constructor)

4. **Services return DTOs, not entities**
   - Never expose JPA entities to controllers
   - Map entities → DTOs in service layer
   - Use manual mapping (no MapStruct in this project)

### Repository Layer

1. **Repositories must not contain business logic**
   - Only data access
   - Query methods are fine
   - No validation, no calculations

2. **Use Spring Data JPA query methods**
   ```java
   List<Transaction> findByAccountIdAndStatus(Long accountId, TransactionStatus status);
   ```

3. **Custom queries: use `@Query` with JPQL**
   ```java
   @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId")
   List<Transaction> findByAccount(@Param("accountId") Long accountId);
   ```

4. **Avoid N+1 queries**
   - Use `@EntityGraph` or JOIN FETCH
   - Check logs for "select" spam

### Async Processing

1. **File processing must be async**
   - Upload returns immediately (HTTP 201)
   - Processing happens in background
   - Status tracked via `BatchStatus` enum

2. **Use `@Async` with named thread pools**
   - Defined in `AsyncConfig.java`
   - `file-processing` = CPU-bound tasks (parsing)
   - `reconciliation` = I/O + compute (matching)

3. **Tasks extend base classes**
   - File uploads don't currently extend a base, but follow pattern
   - Include error handling
   - Update status (PENDING → PROCESSING → COMPLETED/FAILED)

### Exception Handling

1. **Throw domain exceptions**
   - `AccountNotFoundException`
   - `DuplicateFileException`
   - `InvalidFileFormatException`

2. **GlobalExceptionHandler maps to HTTP**
   - 404 for `*NotFoundException`
   - 409 for `Duplicate*Exception`
   - 400 for validation errors
   - 500 for unexpected errors

3. **Never swallow exceptions**
   - Log at error level
   - Rethrow or wrap in domain exception
   - Include context (file name, account ID, etc.)

### Logging

1. **Use SLF4J + Lombok `@Slf4j`**
   ```java
   log.info("Processing bank statement {} for account {}", fileName, accountId);
   log.error("Failed to parse row {}: {}", rowNum, error);
   ```

2. **Log at appropriate levels**
   - `debug` = verbose details
   - `info` = important events (upload started, reconciliation complete)
   - `warn` = recoverable issues (skipped row)
   - `error` = failures requiring attention

3. **Include context in logs**
   - IDs, file names, counts
   - Helps debugging without attaching debugger

---

## Testing Rules

### Unit Tests (Services)

1. **Service layer must have unit tests**
   - Mock repositories with `@Mock`
   - Use `@ExtendWith(MockitoExtension.class)`
   - Test happy path + failure cases

2. **Example pattern**
   ```java
   @ExtendWith(MockitoExtension.class)
   class BankStatementServiceTest {
       @Mock
       private BankStatementRepository repository;
       
       @InjectMocks
       private BankStatementServiceImpl service;
       
       @Test
       void upload_validFile_returnsDto() {
           // Given
           when(repository.save(any())).thenReturn(statement);
           
           // When
           BankStatementDto result = service.upload(file, accountId);
           
           // Then
           assertThat(result.getStatus()).isEqualTo(BatchStatus.PENDING);
           verify(repository).save(any());
       }
   }
   ```

3. **Use AssertJ for assertions**
   ```java
   assertThat(result).isNotNull();
   assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
   assertThat(transactions).hasSize(2);
   ```

### Integration Tests (Controllers)

1. **Controllers must have integration tests**
   - Extend `AbstractControllerTest`
   - Use `MockMvc` for HTTP calls
   - Real database (Testcontainers)

2. **Example pattern**
   ```java
   class BankStatementControllerTest extends AbstractControllerTest {
       @Test
       void upload_validFile_returns201() throws Exception {
           MockMultipartFile file = new MockMultipartFile(...);
           
           mockMvc.perform(multipart("/api/bank-statements/upload")
                   .file(file)
                   .param("accountId", "1")
                   .header("Authorization", bearer(adminToken)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.data.status").value("PENDING"));
       }
   }
   ```

3. **Use Testcontainers for real DB**
   - Configured in `TestcontainersConfiguration.java`
   - MySQL container spins up automatically
   - Each test runs in transaction (auto-rollback)

4. **Test authentication**
   - `AbstractControllerTest` provides `adminToken`, `reconOfficerToken`
   - Test both authorized and unauthorized access

### Test Coverage Goals

- **Services:** 80%+ line coverage
- **Controllers:** HTTP endpoint coverage (auth, validation, status codes)
- **Repositories:** No unit tests (integration tests cover)
- **Parsers:** Unit tests for edge cases (empty files, malformed rows)

### Running Tests

```bash
# Fast feedback: controller tests only
./mvnw test -Dtest="*ControllerTest"

# Before commit: all tests
./mvnw test

# Docker must be running for integration tests
docker ps  # Verify Docker is up
```

---

## Security

### Authentication

1. **JWT tokens**
   - HMAC256 signed
   - 60 minute expiry (configurable)
   - Stored in `Authorization: Bearer <token>` header

2. **Roles**
   - `ADMIN` - full access, can generate demo data
   - `RECON_OFFICER` - upload files, view reports

3. **Securing endpoints**
   ```java
   @PreAuthorize("hasAnyRole('ADMIN', 'RECON_OFFICER')")
   public ResponseEntity<?> uploadStatement(...) {
   ```

4. **Default credentials (change in prod!)**
   - Username: `admin`
   - Password: `password`
   - Seeded on startup via `AdminUserSeeder`

### Configuration

1. **Environment variables**
   ```
   DB_HOST=localhost
   DB_NAME=settlement_service
   DB_USERNAME=settlement
   DB_PASSWORD=settlement
   JWT_SECRET=<base64-key>
   JWT_EXPIRATION_MINUTES=60
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=password
   ```

2. **Never commit secrets**
   - Use `.env` files locally
   - Use environment variables in Docker/Kubernetes
   - Rotate JWT secret in production

---

## Important Instructions for Claude

### Before Writing Code

1. **Check for existing patterns**
   - Read `AbstractControllerTest` before writing tests
   - Check existing services for naming conventions
   - Look at similar endpoints for structure

2. **Understand the domain**
   - Bank statements → Transactions (via parsing)
   - Settlement reports → SettlementTransactions (via parsing)
   - Reconciliation = matching SettlementTransactions to InternalRecords
   - Don't confuse Transaction (bank statement) with SettlementTransaction (report)

3. **Follow file naming**
   - Controllers: `{Entity}Controller.java`
   - Services: `{Entity}Service.java` (interface) + `{Entity}ServiceImpl.java` (implementation)
   - Repositories: `{Entity}Repository.java`
   - Tests: `{Class}Test.java`

### When Adding Features

1. **Full stack implementation**
   - [ ] Create/update entity (if schema change)
   - [ ] Create Flyway migration (if schema change)
   - [ ] Create request/response DTOs
   - [ ] Add repository method (if needed)
   - [ ] Implement service method
   - [ ] Add controller endpoint
   - [ ] Write controller integration test
   - [ ] Write service unit test
   - [ ] Update README.md API reference

2. **After modifying code, suggest required tests**
   - "I added `POST /api/accounts`, you should add `AccountControllerTest#create_validAccount_returns201`"
   - "I changed `BankStatementService.upload()`, update `BankStatementServiceTest` mocks"

### What NOT to Do

❌ **Do not introduce new libraries without approval**
   - Stick to existing: Spring, Hibernate, POI, OpenCSV
   - No MapStruct, no Gson, no custom JSON libraries

❌ **Never change production configuration defaults**
   - Don't modify `application.yml` defaults
   - Don't change Docker Compose settings
   - Use environment variables for overrides

❌ **Do not modify existing Flyway migrations**
   - NEVER edit V01-V26 files
   - Always create new migrations

❌ **Do not add business logic to controllers**
   - Controllers call services, period
   - No calculations, no queries, no validation logic

❌ **Do not use field injection**
   ```java
   // ❌ BAD
   @Autowired
   private AccountRepository repository;
   
   // ✅ GOOD
   @RequiredArgsConstructor
   public class AccountService {
       private final AccountRepository repository;
   }
   ```

### When in Doubt

1. **Look at existing code**
   - `BankStatementController` is a reference implementation
   - `BankStatementService` shows async pattern
   - `AbstractControllerTest` is the test base

2. **Ask before major changes**
   - "Should I add a new thread pool?"
   - "Should I introduce caching?"
   - "Should I refactor the parser?"

3. **Keep it simple**
   - Prefer explicit code over clever abstractions
   - Favor composition over inheritance
   - Don't over-engineer

---

## Common Patterns

### Upload Flow

```java
// 1. Controller receives file
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam MultipartFile file, @RequestParam Long accountId) {
    BankStatementDto result = bankStatementService.upload(file, accountId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SettlementServiceResponse.success("Uploaded", result));
}

// 2. Service validates and persists
@Transactional
public BankStatementDto upload(MultipartFile file, Long accountId) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException(accountId));
    
    // Check duplicate (file hash)
    String hash = calculateHash(file);
    if (bankStatementRepository.existsByFileHashAndAccountId(hash, accountId)) {
        throw new DuplicateFileException();
    }
    
    // Create entity
    BankStatement statement = new BankStatement();
    statement.setAccount(account);
    statement.setFileName(file.getOriginalFilename());
    statement.setFileHash(hash);
    statement.setStatus(BatchStatus.PENDING);
    bankStatementRepository.save(statement);
    
    // Trigger async processing
    applicationEventPublisher.publishEvent(new BankStatementUploadedEvent(statement.getId()));
    
    return toDto(statement);
}

// 3. Async task processes file
@Async("file-processing")
@EventListener
public void process(BankStatementUploadedEvent event) {
    BankStatement statement = repository.findById(event.getId()).orElseThrow();
    statement.setStatus(BatchStatus.PROCESSING);
    repository.save(statement);
    
    try {
        List<Transaction> transactions = parser.parse(statement.getFileContent());
        transactionRepository.saveAll(transactions);
        
        statement.setStatus(BatchStatus.COMPLETED);
        statement.setTotalEntries(transactions.size());
    } catch (Exception e) {
        statement.setStatus(BatchStatus.FAILED);
        log.error("Failed to process statement {}", statement.getId(), e);
    }
    
    repository.save(statement);
}
```

### Reconciliation Pattern

```java
// 1. Match SettlementTransaction to InternalRecord using formula
ReconciliationFormula formula = getFormula(settlementTransaction);
String matchKey = evaluateFormula(formula, settlementTransaction);
Optional<InternalRecord> match = internalRecordRepository.findByMatchKey(matchKey);

// 2. Compare amounts
if (match.isPresent()) {
    InternalRecord internal = match.get();
    if (settlementTransaction.getAmount().equals(internal.getAmount())) {
        settlementTransaction.setReconciliationStatus(ReconciliationStatus.RECONCILED);
    } else {
        settlementTransaction.setReconciliationStatus(ReconciliationStatus.UNRECONCILED);
        createDiscrepancy(settlementTransaction, internal);
    }
} else {
    settlementTransaction.setReconciliationStatus(ReconciliationStatus.MISSING);
}
```

---

## API Versioning

- Current: `/api/` (no version)
- Future: `/api/v1/`, `/api/v2/`
- Breaking changes require new version

---

## Performance Notes

1. **File parsing can be slow**
   - 10k rows = ~10 seconds
   - That's why async processing is mandatory

2. **Reconciliation can be parallelized**
   - Currently sequential
   - Future: parallel per account

3. **Use pagination for large result sets**
   - Default page size: 20
   - Max page size: 100

---

## Troubleshooting

### "Tests fail with Docker errors"
- **Cause:** Docker not running or Testcontainers can't connect
- **Fix:** `docker ps` to verify Docker is up

### "Flyway migration failed"
- **Cause:** Manual schema change or migration conflict
- **Fix:** Drop DB and restart (`docker-compose down -v && docker-compose up`)

### "JWT token expired"
- **Cause:** 60 minute expiry
- **Fix:** Re-login to get new token

### "File upload returns 409 Conflict"
- **Cause:** Same file already uploaded for this account
- **Fix:** Upload different file or delete existing statement

---

## Project Conventions

- **File naming:** PascalCase for classes, camelCase for methods
- **Package naming:** lowercase, no underscores
- **Variable naming:** camelCase
- **Constants:** UPPER_SNAKE_CASE
- **Database columns:** snake_case
- **JSON keys:** camelCase

---

## Key Dependencies

```xml
<!-- Core -->
<spring-boot.version>4.1.0</spring-boot.version>
<java.version>17</java.version>

<!-- Database -->
<mysql-connector.version>8.0.33</mysql-connector.version>
<flyway.version>10.x</flyway.version>

<!-- Security -->
<jjwt.version>0.12.6</jjwt.version>

<!-- File Processing -->
<poi.version>5.x</poi.version>
<opencsv.version>5.x</opencsv.version>

<!-- Testing -->
<testcontainers.version>1.19.x</testcontainers.version>
```

---

## Contact

For questions about this project:
- **Developer:** Precious Michael
- **Email:** precious.michael@moniepoint.com

---

**Remember:** Extend the system, don't reinvent it. Follow patterns, write tests, keep it simple.
