package com.bagusxmahendra.mltf.graph_processing_agent.controller;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.AnalysisResult;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.DynamicDocumentData;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.graph_processing_agent.service.PipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphAnalysisControllerTest {

    @Mock
    private PipelineService mockPipelineService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        GraphAnalysisController controller = new GraphAnalysisController(mockPipelineService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("POST /api/v1/graph/analysis - Should return 200 OK with AnalysisResult")
    void testAnalyzeDocumentGraphEndpoint() {
        // Arrange
        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-CTRL-01", "name", "John Doe"),
                List.of(
                        new DynamicDocumentData("PAYSLIP", Map.of("employer", "Globex", "netPay", 9000.0)),
                        new DynamicDocumentData("BANK_STATEMENT", Map.of("sender", "Globex", "deposit", 9000.0))
                )
        );

        AnalysisResult mockResult = AnalysisResult.approved(List.of());

        when(mockPipelineService.processSalaryAnalysis(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(mockResult));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/graph/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.checkName").isEqualTo("SALARY_TRIANGULATION")
                .jsonPath("$.passed").isEqualTo(true)
                .jsonPath("$.discrepancies").isArray()
                .jsonPath("$.discrepancies.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/v1/graph/analysis - Should return FLAGGED response when discrepancies exist")
    void testAnalyzeDocumentGraphEndpointFlagged() {
        // Arrange
        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-CTRL-02", "name", "Jane Doe"),
                List.of(
                        new DynamicDocumentData("PAYSLIP", Map.of("employer", "Acme", "netPay", 5000.0)),
                        new DynamicDocumentData("BANK_STATEMENT", Map.of("sender", "FakeCorp", "deposit", 3000.0))
                )
        );

        AnalysisResult mockResult = AnalysisResult.flagged(List.of(
                "Employer mismatch: Declared employer 'Acme' does not match bank statement salary sender 'FakeCorp'.",
                "Salary amount mismatch exceeds 5% threshold."
        ));

        when(mockPipelineService.processSalaryAnalysis(any(GraphAnalysisRequest.class)))
                .thenReturn(Mono.just(mockResult));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/graph/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FLAGGED")
                .jsonPath("$.checkName").isEqualTo("SALARY_TRIANGULATION")
                .jsonPath("$.passed").isEqualTo(false)
                .jsonPath("$.discrepancies.length()").isEqualTo(2);
    }
}
