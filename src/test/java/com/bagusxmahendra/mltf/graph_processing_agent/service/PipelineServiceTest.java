package com.bagusxmahendra.mltf.graph_processing_agent.service;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.AnalysisResult;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.DynamicDocumentData;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.StandardizedSalaryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @Mock
    private LlmNormalizationService mockLlmNormalizationService;

    @Mock
    private SpannerGraphService mockSpannerGraphService;

    private PipelineService pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new PipelineService(mockLlmNormalizationService, mockSpannerGraphService);
    }

    @Test
    @DisplayName("Should orchestrate LLM normalization, Spanner Graph triangulation, and data persistence")
    void testProcessSalaryAnalysisPipeline() {
        // Arrange
        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-PIPE-01", "applicantName", "Charlie Brown"),
                List.of(
                        new DynamicDocumentData("PAYSLIP", Map.of("employer", "Acme Inc", "salary", 7000.0)),
                        new DynamicDocumentData("BANK_STATEMENT", Map.of("sender", "Acme Inc", "deposit", 7000.0))
                )
        );

        StandardizedSalaryData standardizedData = new StandardizedSalaryData(
                "Charlie Brown",
                "Acme Inc",
                7000.0,
                "Acme Inc",
                7000.0
        );

        AnalysisResult expectedResult = AnalysisResult.approved(List.of());

        when(mockLlmNormalizationService.normalize(request)).thenReturn(Mono.just(standardizedData));
        when(mockSpannerGraphService.evaluateSalaryDiscrepancies(eq("APP-PIPE-01"), eq("Charlie Brown"), eq(standardizedData)))
                .thenReturn(Mono.just(expectedResult));
        when(mockSpannerGraphService.saveApplicationGraphData(eq("APP-PIPE-01"), eq("Charlie Brown"), eq("APPROVED"), eq(standardizedData)))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(pipelineService.processSalaryAnalysis(request))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(AnalysisResult.STATUS_APPROVED, result.status());
                    assertTrue(result.passed());
                })
                .verifyComplete();

        verify(mockLlmNormalizationService).normalize(request);
        verify(mockSpannerGraphService).evaluateSalaryDiscrepancies("APP-PIPE-01", "Charlie Brown", standardizedData);
        verify(mockSpannerGraphService).saveApplicationGraphData("APP-PIPE-01", "Charlie Brown", "APPROVED", standardizedData);
    }
}
