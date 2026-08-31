-- ============================================================================
-- Google Cloud Spanner DDL Schema & ISO GQL Property Graph
-- Use Case: Document Fraud Triangulation & Salary Cross-Verification
-- ============================================================================

-- 1. Base Loan Applications Table
CREATE TABLE Applications (
    ApplicationId STRING(64) NOT NULL,
    ApplicantName STRING(256) NOT NULL,
    Status STRING(32) NOT NULL,
    CreatedAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true)
) PRIMARY KEY (ApplicationId);

-- 2. Payslips Table (Interleaved in Applications for Colocated Storage)
CREATE TABLE Payslips (
    ApplicationId STRING(64) NOT NULL,
    PayslipId STRING(64) NOT NULL,
    EmployerName STRING(256) NOT NULL,
    NetSalary FLOAT64 NOT NULL,
    PayPeriodStart DATE,
    PayPeriodEnd DATE,
    IssuedDate DATE,
    CreatedAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true)
) PRIMARY KEY (ApplicationId, PayslipId),
  INTERLEAVE IN PARENT Applications ON DELETE CASCADE;

-- 3. Bank Statements Table (Interleaved in Applications for Colocated Storage)
CREATE TABLE BankStatements (
    ApplicationId STRING(64) NOT NULL,
    StatementId STRING(64) NOT NULL,
    SalarySender STRING(256) NOT NULL,
    MonthlyDeposit FLOAT64 NOT NULL,
    AccountNumber STRING(64),
    StatementDate DATE,
    CreatedAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true)
) PRIMARY KEY (ApplicationId, StatementId),
  INTERLEAVE IN PARENT Applications ON DELETE CASCADE;

-- 4. Google Cloud Spanner Property Graph (ISO GQL Standard)
-- Overlays Nodes and Edges on relational tables to enable graph traversal queries
CREATE PROPERTY GRAPH LoanGraph
  NODE TABLES (
    Applications
      KEY (ApplicationId)
      LABEL Application
      PROPERTIES (ApplicationId, ApplicantName, Status),
    Payslips
      KEY (ApplicationId, PayslipId)
      LABEL Payslip
      PROPERTIES (ApplicationId, PayslipId, EmployerName, NetSalary),
    BankStatements
      KEY (ApplicationId, StatementId)
      LABEL BankStatement
      PROPERTIES (ApplicationId, StatementId, SalarySender, MonthlyDeposit)
  )
  EDGE TABLES (
    Payslips AS HasPayslip
      KEY (ApplicationId, PayslipId)
      SOURCE KEY (ApplicationId) REFERENCES Applications (ApplicationId)
      DESTINATION KEY (ApplicationId, PayslipId) REFERENCES Payslips (ApplicationId, PayslipId)
      LABEL HAS_PAYSLIP,
    BankStatements AS HasBankStatement
      KEY (ApplicationId, StatementId)
      SOURCE KEY (ApplicationId) REFERENCES Applications (ApplicationId)
      DESTINATION KEY (ApplicationId, StatementId) REFERENCES BankStatements (ApplicationId, StatementId)
      LABEL HAS_BANK_STATEMENT
  );
