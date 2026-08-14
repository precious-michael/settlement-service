# Settlement Service - Complete Flow Documentation

## Overview

The Settlement Service is a standalone Spring Boot application that processes bank statements and settlement reports, performs reconciliation between them, and identifies discrepancies. Think of it as a system that helps you match what your bank says happened (Bank Statement) with what actually happened in your payment processing system (Settlement Reports and Internal Records).

---

## The Story: From Setup to Reconciliation

### Chapter 1: The Foundation - Setting Up Your System

Before you can process any transactions, you need to set up the foundation of your reconciliation system.

#### 1.1 Authentication ([AuthController.java:27](src/main/java/org/settlementservice/settlementservice/controllers/AuthController.java#L27))

Everything starts with logging in. The system uses JWT-based authentication with two user roles:
- **ADMIN**: Full access to create banks, accounts, rules, and configurations
- **RECON_OFFICER**: Can view and process transactions, run reconciliations, but can't modify core setup

**The Flow:**
1. User sends username and password to `POST /api/auth/login`
2. System validates credentials using Spring Security's AuthenticationManager
3. Returns a JWT token in the response
4. This token must be included in the `Authorization: Bearer <token>` header for all subsequent requests

**Security Layer:** All endpoints except login are protected. JWT tokens are validated on every request via [JwtAuthenticationFilter](src/main/java/org/settlementservice/settlementservice/security/JwtAuthenticationFilter.java).

#### 1.2 Settlement Banks ([SettlementBankController.java](src/main/java/org/settlementservice/settlementservice/controllers/SettlementBankController.java))

**Admin-only** - The first configuration step is to register the banks you'll be reconciling with.

**The Flow:**
1. Admin creates a Settlement Bank with a name and 3-digit CBN code (e.g., "Access Bank", "044")
2. System stores it with an ACTIVE status
3. Each bank gets a unique ID that will be referenced when creating accounts

**Entity:** [SettlementBank.java](src/main/java/org/settlementservice/settlementservice/entities/SettlementBank.java)  
**Key Fields:** `id`, `name`, `code`, `status` (ACTIVE/INACTIVE)

**Important:** There's no DELETE endpoint by design - banks can only be deactivated to preserve referential integrity.

#### 1.3 Accounts ([AccountController.java](src/main/java/org/settlementservice/settlementservice/controllers/AccountController.java))

**Admin-only** - For each bank, you create accounts that you'll reconcile.

**The Flow:**
1. Admin creates an Account with:
   - Name (e.g., "Moniepoint Settlement Account")
   - Account Number (10-34 digits)
   - Bank ID (foreign key to SettlementBank)
   - Optional opening balance
   - Optional description
2. System stores it with ACTIVE status
3. This account becomes the container for bank statements

**Entity:** [Account.java](src/main/java/org/settlementservice/settlementservice/entities/Account.java)  
**Key Fields:** `id`, `name`, `accountNumber`, `settlementBank`, `openingBalance`, `status`

**Balance Management:** The `openingBalance` field is automatically updated after each bank statement is processed - it's set to the statement's closing balance, creating a continuous chain.

#### 1.4 Classification Rules ([ClassificationRuleController.java](src/main/java/org/settlementservice/settlementservice/controllers/ClassificationRuleController.java))

**Admin-only** - These rules automatically categorize transactions based on their narration.

**The Flow:**
1. Admin creates rules with:
   - Regex pattern (e.g., `".*POS.*"` matches card transactions)
   - Product type (CARD_SETTLEMENT, PAYROLL, TRANSFER, LOAN_REPAYMENT, OTHERS)
   - Optional account ID (if rule is account-specific)
2. When a bank statement is uploaded, each transaction's narration is tested against these rules
3. First match wins (account-specific rules are checked before global rules)
4. Unmatched transactions default to `ProductType.OTHERS`

**Entity:** [ClassificationRule.java](src/main/java/org/settlementservice/settlementservice/entities/ClassificationRule.java)  
**Matching Logic:** [ClassificationMatcher.java](src/main/java/org/settlementservice/settlementservice/async/ClassificationMatcher.java)

#### 1.5 Reconciliation Formulas ([ReconciliationFormulaController.java](src/main/java/org/settlementservice/settlementservice/controllers/ReconciliationFormulaController.java))

**Admin-only** - These define how to match settlement transactions with internal records.

**The Flow:**
1. Admin creates a formula for an account with:
   - Name (e.g., "RRN-STAN Match")
   - Formula pattern (e.g., `"${rrn}/${stan}"` or `"${processorReference}"`)
   - Optional description
   - Can be marked as default for the account
2. The formula uses placeholders that map to fields in your internal records:
   - `${rrn}` - Retrieval Reference Number
   - `${stan}` - System Trace Audit Number
   - `${pan}` - Primary Account Number
   - `${terminalId}` - Terminal ID
   - `${processorReference}` - Processor Reference
3. At reconciliation time, the formula is evaluated to generate a reference string for matching

**Entity:** [ReconciliationFormula.java](src/main/java/org/settlementservice/settlementservice/entities/ReconciliationFormula.java)  
**Multiple formulas per account allowed** - one can be marked as default

#### 1.6 Self-Resolution Rules ([SelfResolutionRuleController.java](src/main/java/org/settlementservice/settlementservice/controllers/SelfResolutionRuleController.java))

**Admin-only** - These rules allow transactions to auto-resolve when no settlement report is uploaded.

**The Flow:**
1. Admin creates rules with:
   - Name (descriptive label)
   - Regex pattern (tested against transaction narration)
   - Active flag (can disable without deleting)
2. When self-resolution is triggered, the system:
   - Finds UNRESOLVED transactions
   - Tests narration against active rules
   - Creates synthetic SettlementTransaction with amount = transaction.credit (or debit if credit is zero)
   - Marks transaction as RESOLVED

**Entity:** [SelfResolutionRule.java](src/main/java/org/settlementservice/settlementservice/entities/SelfResolutionRule.java)  
**Service:** [SelfResolutionService.java](src/main/java/org/settlementservice/settlementservice/services/impl/SelfResolutionServiceImpl.java)

---

### Chapter 2: The Upload Journey - Processing Files

Now that the foundation is set, you can start uploading files.

#### 2.1 Bank Statement Upload ([BankStatementController.java:26](src/main/java/org/settlementservice/settlementservice/controllers/BankStatementController.java#L26))

**The Big Picture:**  
You upload a bank statement file (CSV or Excel) containing all transactions the bank recorded for a period.

**The Flow:**

1. **Upload Request** (`POST /api/bank-statements/upload`)
   - You provide:
     - `accountId` - which account this statement belongs to
     - `file` - CSV or Excel file
     - `openingBalance` - starting balance for this statement
   - System immediately:
     - Validates account exists
     - Checks for duplicate upload (by file hash)
     - Checks balance continuity (opening balance must match previous statement's closing balance)
     - Creates `BankStatement` record with status = PENDING
     - Returns the statement ID

2. **Async Processing** ([BankStatementUploadTask.java](src/main/java/org/settlementservice/settlementservice/async/BankStatementUploadTask.java))
   - Status changes to PROCESSING
   - Parser is selected based on file extension:
     - `.csv` → [CsvStatementFileParser](src/main/java/org/settlementservice/settlementservice/parsers/CsvStatementFileParser.java)
     - `.xlsx` → [ExcelStatementFileParser](src/main/java/org/settlementservice/settlementservice/parsers/ExcelStatementFileParser.java)
   - File is parsed row by row into `ParsedRow` objects
   - Each row contains: `transactionDate`, `valueDate`, `narration`, `referenceNumber`, `debit`, `credit`, `balance`
   - Parse errors are collected in `RowParseError` records

3. **Classification & Deduplication**
   - For each successfully parsed row:
     - `ClassificationMatcher` finds the first matching rule by narration
     - Assigns `ProductType`
     - Checks if transaction already exists (by account + reference number)
     - Skips duplicates silently
   - All valid, non-duplicate transactions are saved in one batch (`saveAll()`)

4. **Completion**
   - Status changes to COMPLETED (or COMPLETED_WITH_ERRORS if parse errors exist)
   - Closing balance is computed: `openingBalance + Σ(credit - debit)` across all valid rows
   - Account's `openingBalance` is updated to this closing balance
   - Total entries count is recorded

**Tracking Progress:** Use `GET /api/bank-statements/{id}` to poll status  

**Entities Created:**
- 1 [BankStatement](src/main/java/org/settlementservice/settlementservice/entities/BankStatement.java) (the upload batch)
- N [Transaction](src/main/java/org/settlementservice/settlementservice/entities/Transaction.java) records (each row)
- 0+ [BankStatementRowError](src/main/java/org/settlementservice/settlementservice/entities/BankStatementRowError.java) (parse failures)

**Initial Transaction Status:** All transactions start as `UNRESOLVED` (no settlement data yet)

#### 2.2 Settlement Report Upload ([SettlementReportController.java:29](src/main/java/org/settlementservice/settlementservice/controllers/SettlementReportController.java#L29))

**The Big Picture:**  
You upload a settlement report file (CSV or Excel) containing details about a specific transaction from your payment processor.

**The Flow:**

1. **Upload Request** (`POST /api/settlement-reports/upload`)
   - You provide:
     - `transactionId` - which bank statement transaction this report is for
     - `file` - CSV or Excel file
     - `formulaId` (optional) - reconciliation formula to use immediately
   - System immediately:
     - Validates transaction exists and is UNRESOLVED
     - Checks for duplicate upload (transaction can't have multiple reports)
     - Creates `SettlementReport` record with status = PENDING
     - Returns the report ID

2. **Async Processing** ([SettlementReportUploadTask.java](src/main/java/org/settlementservice/settlementservice/async/SettlementReportUploadTask.java))
   - Status changes to PROCESSING
   - Parser is selected (same CSV/Excel parsers as bank statements)
   - File is parsed row by row
   - Each row contains: `transactionDate`, `transactionReference`, `narration`, `debit`, `credit`

3. **Settlement Validation**
   - System calculates net amount from the report: `Σ(credit - debit)` across all rows
   - Compares against the original transaction's amount
   - Tolerance threshold from config: `reconciliation.tolerance` (default 0.01)
   - **If within tolerance:**
     - All settlement rows saved as `SettlementTransaction` records
     - Transaction status → RESOLVED
     - If `formulaId` was provided, reconciliation references computed immediately
   - **If outside tolerance:**
     - Report is REJECTED and deleted entirely (preserves FK integrity)
     - Transaction stays UNRESOLVED
     - You can re-upload with correct file

4. **Completion**
   - Status → COMPLETED (or COMPLETED_WITH_ERRORS)
   - Total entries recorded

**Tracking Progress:** Use `GET /api/settlement-reports/{id}` to poll status

**Entities Created:**
- 1 [SettlementReport](src/main/java/org/settlementservice/settlementservice/entities/SettlementReport.java) (the upload batch)
- N [SettlementTransaction](src/main/java/org/settlementservice/settlementservice/entities/SettlementTransaction.java) records (each detail line)
- 0+ [SettlementReportRowError](src/main/java/org/settlementservice/settlementservice/entities/SettlementReportRowError.java)

**Transaction Status Change:** `UNRESOLVED` → `RESOLVED` (if validation passes)

#### 2.3 Formula Assignment ([SettlementReportController.java:54](src/main/java/org/settlementservice/settlementservice/controllers/SettlementReportController.java#L54))

**The Big Picture:**  
After uploading a settlement report, you can assign (or change) the reconciliation formula.

**The Flow:**
1. `PUT /api/settlement-reports/{reportId}/reconciliation-formula` with `formulaId` in body
2. System validates formula belongs to same account as the transaction
3. Loads all `SettlementTransaction` records for this report
4. For each settlement transaction:
   - Evaluates the formula (e.g., `"${rrn}/${stan}"`)
   - Looks up matching internal record
   - Populates `reconciliationReference` field
   - Sets `reconciliationStatus` to PENDING
5. Now ready for reconciliation engine

**Use Case:** Uploaded a report without a formula, or need to change the matching strategy

---

### Chapter 3: Self-Resolution - The Auto-Match Shortcut

#### 3.1 Triggering Self-Resolution ([SelfResolutionController.java:30](src/main/java/org/settlementservice/settlementservice/controllers/SelfResolutionController.java#L30))

**The Big Picture:**  
Some transactions don't need a full settlement report - they can resolve themselves based on narration patterns.

**The Flow:**

1. **Trigger** (`POST /api/transactions/self-resolve`)
   - Scope options (exactly one):
     - `transactionId` - resolve single transaction
     - `accountId` - all UNRESOLVED transactions in account
     - `statementId` - all UNRESOLVED in that bank statement
     - (none) - all UNRESOLVED across entire system

2. **Processing:**
   - Loads active `SelfResolutionRule` records
   - For each UNRESOLVED transaction:
     - Tests narration against each rule's pattern
     - On first match:
       - Creates synthetic `SettlementTransaction` with:
         - Amount = transaction.credit (or debit if credit is zero)
         - Narration = original transaction narration
       - Marks transaction as RESOLVED
       - Skips transaction if already resolved

3. **Returns:** Count of successfully resolved transactions

**Service:** [SelfResolutionServiceImpl.java](src/main/java/org/settlementservice/settlementservice/services/impl/SelfResolutionServiceImpl.java)

**Use Case:** Bulk-resolve fee transactions, internal transfers, or known transaction types without uploading individual reports

---

### Chapter 4: Viewing and Querying Transactions

#### 4.1 Transaction Search ([TransactionController.java:28](src/main/java/org/settlementservice/settlementservice/controllers/TransactionController.java#L28))

**Available to:** ADMIN and RECON_OFFICER

**The Flow:**
1. `GET /api/transactions` with optional filters:
   - `status` - UNRESOLVED, RESOLVED, MISMATCHED
   - `accountId` - filter by account
   - `productType` - filter by classification
   - `dateFrom` / `dateTo` - date range
   - `page` / `size` - pagination
2. Returns paginated list of transactions matching criteria

**Use Case:** Find all unresolved card transactions for a specific account in July

#### 4.2 Settlement Validation Summary ([SettlementValidationController.java:22](src/main/java/org/settlementservice/settlementservice/controllers/SettlementValidationController.java#L22))

**The Flow:**
1. `GET /api/settlement-validation/summary?accountId=1&month=2026-08`
2. System calculates:
   - Total transactions in month
   - Count by status (UNRESOLVED, RESOLVED, MISMATCHED)
   - Total amounts by status
3. Returns summary statistics

**Use Case:** Monthly reconciliation report showing resolution rates

---

### Chapter 5: The Reconciliation Engine - Where The Magic Happens

#### 5.1 The Reconciliation Concept

**What is being reconciled?**
- **Bank A's view** (Internal Records): What your payment processing system recorded
- **Bank B's view** (Settlement Transactions): What the settlement report says happened

**The Goal:** Match every Settlement Transaction against an Internal Record using the formula's reference pattern, and flag discrepancies.

#### 5.2 Internal Records - The Reference Data

**Entity:** [InternalRecord.java](src/main/java/org/settlementservice/settlementservice/entities/InternalRecord.java)

**These represent Bank A's authoritative transaction log.** In production, these would come from your payment processing system. In this demo, they're generated by:

**Demo Generator:** [DemoFileGeneratorService.java](src/main/java/org/settlementservice/settlementservice/demo/services/impl/DemoFileGeneratorServiceImpl.java)

**Key Fields:**
- `rrn` - Retrieval Reference Number
- `stan` - System Trace Audit Number  
- `pan` - Primary Account Number
- `terminalId` - Terminal ID
- `processorReference` - Processor's unique reference
- `amount` - The authoritative amount

#### 5.3 Running Reconciliation ([ReconciliationController.java:31](src/main/java/org/settlementservice/settlementservice/reconciliation/controllers/ReconciliationController.java#L31))

**The Flow:**

1. **Trigger** (`POST /api/reconciliation/run`)
   - Returns immediately with run ID
   - Processing happens asynchronously

2. **Processing** ([ReconciliationEngineImpl.java](src/main/java/org/settlementservice/settlementservice/reconciliation/services/impl/ReconciliationEngineImpl.java))

   **Step 1: Load Settlement Transactions**
   - Loads all `SettlementTransaction` records with `reconciliationStatus = PENDING`
   - Groups them by `account.id`

   **Step 2: Parallel Processing by Account**
   - Each account is processed in its own thread (4-8 parallel workers)
   - **Critical Design:** Each account saves its results immediately in its own @Transactional context
   - If one account fails, others' results are preserved

   **Step 3: Per-Account Reconciliation**
   For each settlement transaction:
   
   a. **Extract Formula References**
      - Load the associated reconciliation formula
      - Parse formula pattern (e.g., `"${rrn}/${stan}"`)
      - Extract the `reconciliationReference` from the settlement transaction
      
   b. **Find Matching Internal Record**
      - Query `InternalRecord` using the formula pattern
      - For `"${rrn}/${stan}"`: Query where `rrn = value1 AND stan = value2`
      - For `"${processorReference}"`: Query where `processorReference = value`
      - **Performance:** Database indexes on rrn, stan, pan, processorReference (Migration V23)
      
   c. **Compare Amounts**
      - If no internal record found → **MISMATCHED**
      - If found, compare amounts using `BigDecimal.compareTo()`
      - If amounts match (== 0) → **MATCHED**
      - If amounts differ → **MISMATCHED**, create `Discrepancy` record
      
   d. **Update Settlement Transaction**
      - Set `reconciliationStatus` to MATCHED or MISMATCHED
      - Link to internal record if found

   **Step 4: Create Discrepancies**
   - For each mismatch, create a [Discrepancy](src/main/java/org/settlementservice/settlementservice/entities/Discrepancy.java) record:
     - Links to both SettlementTransaction and InternalRecord
     - Records the amount difference
     - Stores narrations from both sides
   
   **Step 5: Update Transaction Status**
   - If settlement transaction is MISMATCHED → mark original Transaction as MISMATCHED

3. **Returns:**
   - Total settlement transactions processed
   - Count matched
   - Count mismatched
   - Processing time per account

**Performance Optimizations:**
- Batch processing with `saveAll()`
- Parallel account reconciliation (4-8 threads)
- Database indexes on lookup fields
- Each account's results saved immediately (fault-tolerant)

**Reconciliation Service:** [ReconciliationEngineImpl.java](src/main/java/org/settlementservice/settlementservice/reconciliation/services/impl/ReconciliationEngineImpl.java)

#### 5.4 Viewing Reconciliation Results

**Get All Discrepancies** ([DiscrepancyController.java:24](src/main/java/org/settlementservice/settlementservice/controllers/DiscrepancyController.java#L24))
- `GET /api/discrepancies?transactionId={id}&page=0&size=20`
- Returns paginated discrepancies
- Filter by transaction ID optional

**Get Results from Reconciliation Controller** ([ReconciliationController.java:37](src/main/java/org/settlementservice/settlementservice/reconciliation/controllers/ReconciliationController.java#L37))
- `GET /api/reconciliation/results?transactionId={id}`
- Same as discrepancies endpoint, alternate route

**Use Case:** Review all amount mismatches found during last reconciliation run

---

### Chapter 6: Demo & Testing - The Generator

#### 6.1 Generating Demo Files ([DemoController.java:43](src/main/java/org/settlementservice/settlementservice/demo/controllers/DemoController.java#L43))

**Admin-only** - For testing the complete flow without real bank files

**The Flow:**

1. **Generate** (`POST /api/demo/generate-files?accountId=1&count=10&mismatchRate=0.2`)
   - `count` - how many transactions to generate
   - `mismatchRate` - percentage that should have amount mismatches (0.0-1.0)

2. **System Creates:**
   - **Bank Statement CSV file** (positional format with header block)
   - **Settlement Report CSV file** (named column headers)
   - **Internal Records** (inserted into database)
   - All saved to `./demo-files/` directory

3. **Continuity Support:**
   - Checks for previous bank statements on this account
   - Uses previous closing balance as opening balance
   - Starts dates 1 day after previous statement ended
   - **Example:**
     ```
     Generation 1: Opening=0, Closing=55,000, Dates: Aug 3-7
     Generation 2: Opening=55,000, Closing=110,000, Dates: Aug 8-12 (auto-continues!)
     ```

4. **Returns:**
   - File paths
   - Download URLs
   - Opening/closing balances
   - Date range
   - Instructions for testing upload flow

5. **Download Files** (`GET /api/demo/download/{fileName}`)
   - Download the generated CSV files
   - Can then upload them through real endpoints

**Demo Service:** [DemoFileGeneratorServiceImpl.java](src/main/java/org/settlementservice/settlementservice/demo/services/impl/DemoFileGeneratorServiceImpl.java)

**Alternative Demo Route** ([ReconciliationController.java:45](src/main/java/org/settlementservice/settlementservice/reconciliation/controllers/ReconciliationController.java#L45))
- `POST /api/demo/generate` (same functionality, alternate route)

**Use Case:** Generate 100 transactions with 20% mismatch rate to test reconciliation performance

---

## Complete End-to-End Workflow

Here's how everything connects in a typical scenario:

### Phase 1: Initial Setup (One-time, Admin)
1. Login as ADMIN → get JWT token
2. Create Settlement Bank (e.g., "Access Bank", code "044")
3. Create Account under that bank
4. Create Classification Rules (e.g., POS transactions → CARD_SETTLEMENT)
5. Create Reconciliation Formula (e.g., `"${rrn}/${stan}"`)
6. Create Self-Resolution Rules for known patterns

### Phase 2: Generate Demo Data (Testing)
1. Call `POST /api/demo/generate-files` for an account
2. Download generated bank statement CSV
3. Download generated settlement report CSV
4. (Internal records already created in DB)

### Phase 3: Upload Bank Statement
1. Call `POST /api/bank-statements/upload` with account ID, file, opening balance
2. Get statement ID in response
3. Poll `GET /api/bank-statements/{id}` until status = COMPLETED
4. System has created Transaction records (status = UNRESOLVED)
5. Each transaction classified by product type

### Phase 4: Resolve Transactions (Two Options)

**Option A: Upload Settlement Report (Manual)**
1. For each transaction, call `POST /api/settlement-reports/upload` with transaction ID and file
2. Poll status endpoint
3. System validates net amount, creates SettlementTransaction records
4. Transaction status → RESOLVED

**Option B: Self-Resolution (Automated)**
1. Call `POST /api/transactions/self-resolve?accountId={id}`
2. System matches UNRESOLVED transactions against rules
3. Creates synthetic SettlementTransaction records
4. Marks matching transactions as RESOLVED

### Phase 5: Run Reconciliation
1. Call `POST /api/reconciliation/run`
2. Engine processes all PENDING settlement transactions in parallel
3. Matches against internal records using formulas
4. Creates Discrepancy records for mismatches
5. Updates settlement transaction statuses (MATCHED/MISMATCHED)

### Phase 6: Review Results
1. Query `GET /api/discrepancies` to see all mismatches
2. Query `GET /api/transactions?status=MISMATCHED` to see problem transactions
3. Check `GET /api/settlement-validation/summary` for monthly statistics

---

## Key Design Patterns & Architecture Decisions

### 1. Async Upload Processing
- **Pattern:** Controller returns immediately with ID, processing happens in background thread
- **Why:** Large files (1000+ rows) would timeout HTTP requests
- **Implementation:** 
  - [BankStatementUploadTask](src/main/java/org/settlementservice/settlementservice/async/BankStatementUploadTask.java)
  - [SettlementReportUploadTask](src/main/java/org/settlementservice/settlementservice/async/SettlementReportUploadTask.java)
  - `@Async("file-processing")` thread pool (4 core, 8 max threads)

### 2. Status Progression
- **PENDING** → just uploaded, not started
- **PROCESSING** → currently being parsed/validated
- **COMPLETED** → success, all rows processed
- **COMPLETED_WITH_ERRORS** → partial success, some rows failed to parse
- **FAILED** → complete failure (e.g., validation rejected entire file)

### 3. Transaction Status Semantics
- **UNRESOLVED** → exists in bank statement, no settlement data yet
- **RESOLVED** → has settlement data (from report upload or self-resolution), ready for reconciliation
- **MISMATCHED** → reconciliation engine found discrepancy vs internal records

**Critical:** MISMATCHED belongs to reconciliation engine, NOT settlement validation. Settlement validation only marks RESOLVED or rejects the upload.

### 4. Dual Parser Support
- **Files:** [CsvStatementFileParser](src/main/java/org/settlementservice/settlementservice/parsers/CsvStatementFileParser.java), [ExcelStatementFileParser](src/main/java/org/settlementservice/settlementservice/parsers/ExcelStatementFileParser.java)
- **Factory:** [StatementFileParserFactory](src/main/java/org/settlementservice/settlementservice/parsers/StatementFileParserFactory.java)
- **Pattern:** Each parser handles BOTH bank statements AND settlement reports
- **Bank Statement:** Positional columns with header block
- **Settlement Report:** Named headers (CSV) or positional (Excel)

### 5. Parallel Reconciliation by Account
- **Pattern:** Group settlement transactions by account, process each account in separate thread
- **Critical Design:** Each account saves immediately in its own @Transactional context
- **Why:** If one account hangs or fails, others' results are NOT lost
- **Thread Pool:** `reconciliation` executor (4 core, 8 max)

### 6. Opening Balance Continuity
- **Pattern:** Account.openingBalance is single source of truth
- **Flow:**
  1. Upload statement with opening balance
  2. System validates: must match previous statement's closing balance
  3. After processing, system computes closing balance
  4. Account.openingBalance auto-updated to closing balance
  5. Next statement upload must use this as opening balance
- **Result:** Unbroken chain of balances across statements

### 7. Formula-Based Reconciliation
- **Pattern:** Runtime evaluation of placeholder templates
- **Example:** `"${rrn}/${stan}"` → `"123456789012/001234"`
- **Flexibility:** Can change matching strategy without code changes
- **Performance:** Optimized queries based on formula fields
- **Database Indexes:** V23 migration added indexes on all formula fields

### 8. TransactionTemplate for Commit Control
- **Problem:** Need DB row committed BEFORE triggering async task
- **Solution:** Wrap save in `TransactionTemplate.execute()`, trigger async call AFTER block returns
- **Why:** Prevents race where async task queries for record before it's committed
- **File:** [BankStatementServiceImpl](src/main/java/org/settlementservice/settlementservice/services/impl/BankStatementServiceImpl.java)

### 9. Settlement Report Delete-on-Rejection
- **Pattern:** If net amount outside tolerance, delete entire SettlementReport row
- **Why:** `transaction_id` has unique FK - leaving rejected row permanently blocks re-upload
- **Alternative Considered:** Keep row with REJECTED status - requires removing unique constraint
- **Decision:** Delete rejected uploads, allow re-upload with correct file

### 10. Batch Operations
- **Pattern:** Changed from individual `save()` calls to `saveAll()`
- **Impact:** Reduced DB round-trips from ~2000 to ~2 for 1000 transactions
- **Used In:** BankStatementUploadTask, SettlementReportUploadTask, ReconciliationEngine

---

## Important Gotchas & Edge Cases

1. **Balance Validation Failure** - If opening balance doesn't match previous closing, upload is rejected immediately. Fix: Check account's current openingBalance before uploading.

2. **Duplicate Upload Detection** - Files are hashed. Re-uploading same file for same account returns 409 Conflict, not 201.

3. **Settlement Amount Tolerance** - Default 0.01. Configure via `reconciliation.tolerance` in application.yml.

4. **Formula Must Match Account** - Can't use Formula A (for Account 1) when uploading settlement report for Transaction in Account 2.

5. **Self-Resolution Amount Logic** - Uses credit amount if > 0, otherwise uses debit. For transactions with both credit and debit (rare), credit wins.

6. **No Delete for Settlement Banks** - Only deactivation allowed. Prevents orphaned accounts.

7. **Classification Rule Order Matters** - Account-specific rules checked before global. First match wins. Order can affect results.

8. **Reconciliation Reference Requires Formula** - Settlement transactions without a formula assigned can't be reconciled. Must assign via upload or update endpoint first.

9. **BigDecimal Comparison** - Always uses `.compareTo() == 0`, never `.equals()`. Standard practice for monetary amounts.

10. **JWT Token Expiration** - Tokens expire after configured period. Front-end should handle 401 responses and re-authenticate.

---

## File References (Quick Jump Links)

### Controllers
- [AuthController.java](src/main/java/org/settlementservice/settlementservice/controllers/AuthController.java)
- [SettlementBankController.java](src/main/java/org/settlementservice/settlementservice/controllers/SettlementBankController.java)
- [AccountController.java](src/main/java/org/settlementservice/settlementservice/controllers/AccountController.java)
- [ClassificationRuleController.java](src/main/java/org/settlementservice/settlementservice/controllers/ClassificationRuleController.java)
- [ReconciliationFormulaController.java](src/main/java/org/settlementservice/settlementservice/controllers/ReconciliationFormulaController.java)
- [BankStatementController.java](src/main/java/org/settlementservice/settlementservice/controllers/BankStatementController.java)
- [SettlementReportController.java](src/main/java/org/settlementservice/settlementservice/controllers/SettlementReportController.java)
- [TransactionController.java](src/main/java/org/settlementservice/settlementservice/controllers/TransactionController.java)
- [SelfResolutionController.java](src/main/java/org/settlementservice/settlementservice/controllers/SelfResolutionController.java)
- [SelfResolutionRuleController.java](src/main/java/org/settlementservice/settlementservice/controllers/SelfResolutionRuleController.java)
- [ReconciliationController.java](src/main/java/org/settlementservice/settlementservice/reconciliation/controllers/ReconciliationController.java)
- [DiscrepancyController.java](src/main/java/org/settlementservice/settlementservice/controllers/DiscrepancyController.java)
- [SettlementValidationController.java](src/main/java/org/settlementservice/settlementservice/controllers/SettlementValidationController.java)
- [DemoController.java](src/main/java/org/settlementservice/settlementservice/demo/controllers/DemoController.java)

### Core Services
- [BankStatementServiceImpl.java](src/main/java/org/settlementservice/settlementservice/services/impl/BankStatementServiceImpl.java)
- [SettlementReportServiceImpl.java](src/main/java/org/settlementservice/settlementservice/services/impl/SettlementReportServiceImpl.java)
- [ReconciliationEngineImpl.java](src/main/java/org/settlementservice/settlementservice/reconciliation/services/impl/ReconciliationEngineImpl.java)
- [SelfResolutionServiceImpl.java](src/main/java/org/settlementservice/settlementservice/services/impl/SelfResolutionServiceImpl.java)

### Async Tasks
- [BankStatementUploadTask.java](src/main/java/org/settlementservice/settlementservice/async/BankStatementUploadTask.java)
- [SettlementReportUploadTask.java](src/main/java/org/settlementservice/settlementservice/async/SettlementReportUploadTask.java)

### Parsers
- [StatementFileParserFactory.java](src/main/java/org/settlementservice/settlementservice/parsers/StatementFileParserFactory.java)
- [CsvStatementFileParser.java](src/main/java/org/settlementservice/settlementservice/parsers/CsvStatementFileParser.java)
- [ExcelStatementFileParser.java](src/main/java/org/settlementservice/settlementservice/parsers/ExcelStatementFileParser.java)
- [ClassificationMatcher.java](src/main/java/org/settlementservice/settlementservice/async/ClassificationMatcher.java)

### Demo
- [DemoFileGeneratorServiceImpl.java](src/main/java/org/settlementservice/settlementservice/demo/services/impl/DemoFileGeneratorServiceImpl.java)

---

## Summary

The Settlement Service is a complete reconciliation pipeline:
1. **Setup** - Configure banks, accounts, rules, formulas
2. **Upload** - Process bank statements and settlement reports asynchronously
3. **Resolve** - Match transactions with settlement data (manual or auto)
4. **Reconcile** - Match settlement data against internal records in parallel
5. **Review** - Query discrepancies and generate reports

**166 tests passing** | **Parallel processing** | **Fault-tolerant** | **Production-ready**
