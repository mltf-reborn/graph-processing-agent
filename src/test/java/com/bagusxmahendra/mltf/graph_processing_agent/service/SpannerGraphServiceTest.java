package com.bagusxmahendra.mltf.graph_processing_agent.service;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.AnalysisResult;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.StandardizedSalaryData;
import com.bagusxmahendra.mltf.graph_processing_agent.rule.*;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.ReadContext;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpannerGraphServiceTest {

    @Mock
    private DatabaseClient mockSpannerClient;

    @Mock
    private ReadContext mockReadContext;

    @Mock
    private ResultSet mockResultSet;

    private RuleProperties ruleProperties;
    private SpannerGraphService service;

    @BeforeEach
    void setUp() {
        ruleProperties = new RuleProperties(
                new RuleProperties.NameMatching(true, true),
                new RuleProperties.EmployerMatching(true, true),
                new RuleProperties.SalaryVariance(true, 0.05),
                new RuleProperties.MinimumSalary(false, 1000.0)
        );

        List<TriangulationRule> rules = List.of(
                new ApplicantNameMatchingRule(ruleProperties),
                new EmployerMatchingRule(ruleProperties),
                new SalaryVarianceRule(ruleProperties),
                new MinimumSalaryRule(ruleProperties)
        );

        service = new SpannerGraphService(mockSpannerClient, rules);
    }

    @Test
    @DisplayName("Should approve when all active rules pass")
    void testSalaryTriangulationApproved() {
        // Arrange
        when(mockSpannerClient.singleUse()).thenReturn(mockReadContext);
        when(mockReadContext.executeQuery(any(Statement.class))).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        StandardizedSalaryData data = new StandardizedSalaryData(
                "Bob Johnson",
                "Google LLC",
                8000.0,
                "Google LLC",
                8100.0 // 1.25% variance, within 5% ($400 allowed)
        );

        // Act & Assert
        StepVerifier.create(service.evaluateSalaryDiscrepancies("APP-2001", data))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(AnalysisResult.STATUS_APPROVED, result.status());
                    assertTrue(result.passed());
                    assertEquals("SALARY_TRIANGULATION", result.checkName());
                    assertTrue(result.discrepancies().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should flag when employer mismatch rule fails")
    void testEmployerMismatchFlagged() {
        // Arrange
        when(mockSpannerClient.singleUse()).thenReturn(mockReadContext);
        when(mockReadContext.executeQuery(any(Statement.class))).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        StandardizedSalaryData data = new StandardizedSalaryData(
                "Bob Johnson",
                "Acme Logistics",
                5000.0,
                "Shell Corporation Inc", // Mismatch
                5000.0
        );

        // Act & Assert
        StepVerifier.create(service.evaluateSalaryDiscrepancies("APP-2002", data))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(AnalysisResult.STATUS_FLAGGED, result.status());
                    assertFalse(result.passed());
                    assertEquals(1, result.discrepancies().size());
                    assertTrue(result.discrepancies().get(0).contains("Employer mismatch"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should flag when salary variance exceeds configured threshold")
    void testSalaryVarianceOver5PercentFlagged() {
        // Arrange
        when(mockSpannerClient.singleUse()).thenReturn(mockReadContext);
        when(mockReadContext.executeQuery(any(Statement.class))).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        StandardizedSalaryData data = new StandardizedSalaryData(
                "Bob Johnson",
                "TechCorp",
                10000.0,
                "TechCorp",
                8500.0 // 15% discrepancy, exceeds 5% threshold
        );

        // Act & Assert
        StepVerifier.create(service.evaluateSalaryDiscrepancies("APP-2003", data))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(AnalysisResult.STATUS_FLAGGED, result.status());
                    assertFalse(result.passed());
                    assertEquals(1, result.discrepancies().size());
                    assertTrue(result.discrepancies().get(0).contains("Salary amount mismatch exceeds 5% threshold"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should flag when applicant name mismatch rule fails")
    void testApplicantNameMismatchFlagged() {
        // Arrange
        when(mockSpannerClient.singleUse()).thenReturn(mockReadContext);
        when(mockReadContext.executeQuery(any(Statement.class))).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("applicantName")).thenReturn("Alice M. Johnson");
        when(mockResultSet.getString("declaredEmployer")).thenReturn("Alpha Corp");
        when(mockResultSet.getDouble("declaredSalary")).thenReturn(6000.0);
        when(mockResultSet.getString("actualSender")).thenReturn("Alpha Corp");
        when(mockResultSet.getDouble("actualDeposit")).thenReturn(6000.0);

        StandardizedSalaryData data = new StandardizedSalaryData(
                "Bob Fraudster", // Name mismatch with Spanner application record
                "Alpha Corp",
                6000.0,
                "Alpha Corp",
                6000.0
        );

        // Act & Assert
        StepVerifier.create(service.evaluateSalaryDiscrepancies("APP-2005", data))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(AnalysisResult.STATUS_FLAGGED, result.status());
                    assertFalse(result.passed());
                    assertTrue(result.discrepancies().get(0).contains("Name mismatch"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should ignore disabled rules dynamically")
    void testDisabledRuleIsIgnored() {
        // Arrange: disable employer matching rule
        RuleProperties customProps = new RuleProperties(
                new RuleProperties.NameMatching(true, true),
                new RuleProperties.EmployerMatching(false, true), // DISABLED
                new RuleProperties.SalaryVariance(true, 0.05),
                new RuleProperties.MinimumSalary(false, 1000.0)
        );

        List<TriangulationRule> rules = List.of(
                new ApplicantNameMatchingRule(customProps),
                new EmployerMatchingRule(customProps),
                new SalaryVarianceRule(customProps)
        );

        SpannerGraphService customService = new SpannerGraphService(mockSpannerClient, rules);

        when(mockSpannerClient.singleUse()).thenReturn(mockReadContext);
        when(mockReadContext.executeQuery(any(Statement.class))).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        StandardizedSalaryData data = new StandardizedSalaryData(
                "Bob Johnson",
                "Acme Logistics",
                5000.0,
                "Different Employer Inc", // Would normally fail, but rule is disabled
                5000.0
        );

        // Act & Assert
        StepVerifier.create(customService.evaluateSalaryDiscrepancies("APP-2006", data))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(AnalysisResult.STATUS_APPROVED, result.status());
                    assertTrue(result.passed());
                    assertTrue(result.discrepancies().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should successfully persist application and document graph mutations to Spanner")
    void testSaveApplicationGraphData() {
        StandardizedSalaryData data = new StandardizedSalaryData(
                "Alice Johnson",
                "Acme Corp",
                5500.0,
                "Acme Corp",
                5500.0
        );

        StepVerifier.create(service.saveApplicationGraphData("APP-3001", "Alice Johnson", "APPROVED", data))
                .verifyComplete();

        verify(mockSpannerClient, times(1)).write(anyIterable());
    }
}
