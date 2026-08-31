package com.bagusxmahendra.mltf.graph_processing_agent.service;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.DynamicDocumentData;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.StandardizedSalaryData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmNormalizationServiceTest {

    @Mock
    private Client mockClient;

    @Mock
    private Models mockModels;

    @Mock
    private GenerateContentResponse mockResponse;

    private ObjectMapper objectMapper;
    private LlmNormalizationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(mockClient, "models", mockModels);
        service = new LlmNormalizationService(mockClient, objectMapper, "gemini-3.5-flash-lite");
    }

    @Test
    @DisplayName("Should successfully normalize raw document OCR into StandardizedSalaryData")
    void testNormalizeSuccess() throws Exception {
        // Arrange
        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-1001", "applicantName", "Alice Smith"),
                List.of(
                        new DynamicDocumentData("PAYSLIP", Map.of("employer", "Acme Corp", "net_amount", 6500.0)),
                        new DynamicDocumentData("BANK_STATEMENT", Map.of("deposit_sender", "Acme Corp", "deposit_val", 6500.0))
                )
        );

        String mockLlmJson = """
                ```json
                {
                  "applicationName": "Alice Smith",
                  "payslipEmployer": "Acme Corp",
                  "payslipNetSalary": 6500.0,
                  "bankStatementSalarySender": "Acme Corp",
                  "bankStatementMonthlyDeposit": 6500.0
                }
                ```
                """;

        when(mockModels.generateContent(eq("gemini-3.5-flash-lite"), anyString(), isNull())).thenReturn(mockResponse);
        when(mockResponse.text()).thenReturn(mockLlmJson);

        // Act & Assert
        StepVerifier.create(service.normalize(request))
                .assertNext(data -> {
                    assertNotNull(data);
                    assertEquals("Alice Smith", data.applicationName());
                    assertEquals("Acme Corp", data.payslipEmployer());
                    assertEquals(6500.0, data.payslipNetSalary());
                    assertEquals("Acme Corp", data.bankStatementSalarySender());
                    assertEquals(6500.0, data.bankStatementMonthlyDeposit());
                })
                .verifyComplete();
    }
}
