package com.bagusxmahendra.mltf.graph_processing_agent.service;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.AnalysisResult;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.StandardizedSalaryData;
import com.bagusxmahendra.mltf.graph_processing_agent.rule.TriangulationContext;
import com.bagusxmahendra.mltf.graph_processing_agent.rule.TriangulationRule;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reactive service executing ISO GQL graph queries against Google Cloud Spanner Graph
 * and evaluating pluggable, configurable triangulation & fraud detection rules.
 * Also handles persistent storage of applications and document graph nodes into Spanner.
 */
@Service
public class SpannerGraphService {

    private static final Logger log = LoggerFactory.getLogger(SpannerGraphService.class);

    private final DatabaseClient spannerClient;
    private final List<TriangulationRule> rules;

    public SpannerGraphService(DatabaseClient spannerClient, List<TriangulationRule> rules) {
        this.spannerClient = spannerClient;
        this.rules = rules != null ? rules : List.of();
    }

    /**
     * Evaluates salary and employer triangulation discrepancies for an application.
     */
    public Mono<AnalysisResult> evaluateSalaryDiscrepancies(String applicationId, StandardizedSalaryData data) {
        return evaluateSalaryDiscrepancies(applicationId, null, data);
    }

    /**
     * Evaluates salary, employer, and applicant name triangulation discrepancies for an application
     * by querying the Spanner Property Graph (ISO GQL) and evaluating all enabled rules.
     * Offloads the blocking Spanner gRPC I/O onto Schedulers.boundedElastic().
     *
     * @param applicationId the unique application identifier
     * @param declaredApplicantName the applicant name from loanApplication payload
     * @param data the normalized salary data extracted via LLM
     * @return Mono emitting the final AnalysisResult
     */
    public Mono<AnalysisResult> evaluateSalaryDiscrepancies(
            String applicationId,
            String declaredApplicantName,
            StandardizedSalaryData data) {
        return Mono.fromCallable(() -> {
            log.info("Executing Spanner Graph ISO GQL evaluation for application: {}", applicationId);
            List<String> discrepancies = new ArrayList<>();

            // 1. ISO GQL Property Graph query to traverse Application -> Payslip and Application -> BankStatement
            String gqlQuery = """
                GRAPH LoanGraph
                MATCH (a:Application {ApplicationId: @appId})-[:HAS_PAYSLIP]->(p:Payslip),
                      (a)-[:HAS_BANK_STATEMENT]->(b:BankStatement)
                RETURN a.ApplicationId AS applicationId,
                       a.ApplicantName AS applicantName,
                       p.EmployerName AS declaredEmployer,
                       p.NetSalary AS declaredSalary,
                       b.SalarySender AS actualSender,
                       b.MonthlyDeposit AS actualDeposit
                """;

            Statement statement = Statement.newBuilder(gqlQuery)
                    .bind("appId").to(applicationId)
                    .build();

            boolean recordFoundInGraph = false;

            try (ResultSet resultSet = spannerClient.singleUse().executeQuery(statement)) {
                while (resultSet.next()) {
                    recordFoundInGraph = true;
                    String applicantName = resultSet.getString("applicantName");
                    String declaredEmployer = resultSet.getString("declaredEmployer");
                    double declaredSalary = resultSet.getDouble("declaredSalary");
                    String actualSender = resultSet.getString("actualSender");
                    double actualDeposit = resultSet.getDouble("actualDeposit");

                    log.debug("Graph match: applicantName='{}', declaredEmployer='{}', declaredSalary={}, actualSender='{}', actualDeposit={}",
                            applicantName, declaredEmployer, declaredSalary, actualSender, actualDeposit);

                    TriangulationContext context = TriangulationContext.builder()
                            .applicationId(applicationId)
                            .applicationApplicantName(applicantName != null ? applicantName : declaredApplicantName)
                            .documentApplicantName(data != null ? data.applicationName() : applicantName)
                            .declaredEmployer(declaredEmployer)
                            .declaredSalary(declaredSalary)
                            .actualSender(actualSender)
                            .actualDeposit(actualDeposit)
                            .build();

                    evaluateRules(context, discrepancies);
                }
            } catch (Exception e) {
                log.warn("Spanner Graph query encountered an exception (e.g. table/graph not yet populated or offline): {}. Evaluating using normalized LLM payload.", e.getMessage());
            }

            // If the graph does not yet have persistent records for this uncommitted application,
            // evaluate triangulation directly from the LLM-normalized payload
            if (!recordFoundInGraph && data != null) {
                log.info("Evaluating triangulation directly from LLM-standardized data payload for application: {}", applicationId);
                String appName = declaredApplicantName != null ? declaredApplicantName : data.applicationName();
                TriangulationContext context = TriangulationContext.builder()
                        .applicationId(applicationId)
                        .applicationApplicantName(appName)
                        .documentApplicantName(data.applicationName())
                        .declaredEmployer(data.payslipEmployer())
                        .declaredSalary(data.payslipNetSalary())
                        .actualSender(data.bankStatementSalarySender())
                        .actualDeposit(data.bankStatementMonthlyDeposit())
                        .build();

                evaluateRules(context, discrepancies);
            }

            // Assemble AnalysisResult
            boolean passed = discrepancies.isEmpty();
            String status = passed ? AnalysisResult.STATUS_APPROVED : AnalysisResult.STATUS_FLAGGED;

            log.info("Analysis completed for {}: status={}, discrepanciesCount={}", applicationId, status, discrepancies.size());
            return new AnalysisResult(status, AnalysisResult.CHECK_SALARY_TRIANGULATION, passed, discrepancies);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Persists the normalized application and associated payslip and bank statement documents
     * into Google Cloud Spanner tables (Applications, Payslips, BankStatements).
     * Offloaded onto Schedulers.boundedElastic().
     *
     * @param applicationId unique application id
     * @param applicantName applicant name
     * @param status analysis status (e.g. APPROVED, FLAGGED, EVALUATED)
     * @param data normalized salary data
     * @return Mono<Void> indicating write completion
     */
    public Mono<Void> saveApplicationGraphData(
            String applicationId,
            String applicantName,
            String status,
            StandardizedSalaryData data) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("Persisting loan application and document graph nodes to Spanner for applicationId: {}", applicationId);
                List<Mutation> mutations = new ArrayList<>();

                String name = applicantName != null && !applicantName.isBlank() ? applicantName :
                        (data != null && data.applicationName() != null ? data.applicationName() : "UNKNOWN");

                // 1. Applications row (Node: Application)
                mutations.add(Mutation.newInsertOrUpdateBuilder("Applications")
                        .set("ApplicationId").to(applicationId)
                        .set("ApplicantName").to(name)
                        .set("Status").to(status != null ? status : "EVALUATED")
                        .set("CreatedAt").to(Value.COMMIT_TIMESTAMP)
                        .build());

                if (data != null) {
                    // 2. Payslips row (Node: Payslip & Edge: HasPayslip)
                    mutations.add(Mutation.newInsertOrUpdateBuilder("Payslips")
                            .set("ApplicationId").to(applicationId)
                            .set("PayslipId").to("PS-" + applicationId)
                            .set("EmployerName").to(data.payslipEmployer() != null ? data.payslipEmployer() : "UNKNOWN")
                            .set("NetSalary").to(data.payslipNetSalary() != null ? data.payslipNetSalary() : 0.0)
                            .set("CreatedAt").to(Value.COMMIT_TIMESTAMP)
                            .build());

                    // 3. BankStatements row (Node: BankStatement & Edge: HasBankStatement)
                    mutations.add(Mutation.newInsertOrUpdateBuilder("BankStatements")
                            .set("ApplicationId").to(applicationId)
                            .set("StatementId").to("BS-" + applicationId)
                            .set("SalarySender").to(data.bankStatementSalarySender() != null ? data.bankStatementSalarySender() : "UNKNOWN")
                            .set("MonthlyDeposit").to(data.bankStatementMonthlyDeposit() != null ? data.bankStatementMonthlyDeposit() : 0.0)
                            .set("CreatedAt").to(Value.COMMIT_TIMESTAMP)
                            .build());
                }

                spannerClient.write(mutations);
                log.info("Successfully committed Spanner mutations for application: {}", applicationId);
            } catch (Exception e) {
                log.warn("Failed to persist data to Spanner (e.g. offline/mock instance): {}", e.getMessage());
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    /**
     * Executes all enabled TriangulationRules against the evaluation context.
     */
    private void evaluateRules(TriangulationContext context, List<String> discrepancies) {
        for (TriangulationRule rule : rules) {
            if (rule.isEnabled()) {
                log.debug("Evaluating rule [{}] ({})", rule.ruleId(), rule.ruleName());
                Optional<String> error = rule.evaluate(context);
                if (error.isPresent()) {
                    String msg = error.get();
                    if (!discrepancies.contains(msg)) {
                        discrepancies.add(msg);
                    }
                }
            } else {
                log.debug("Rule [{}] is disabled, skipping.", rule.ruleId());
            }
        }
    }
}
