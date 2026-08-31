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

    @Test
    @DisplayName("Should normalize bank statement with delimited transaction descriptions and salary deposits")
    void testNormalizeBankStatementWithDelimitedTransactions() throws Exception {
        // Arrange
        Map<String, Object> bankStatementData = Map.ofEntries(
                Map.entry("bankName", "BANK XYZ BERHAD"),
                Map.entry("statementPeriod", "01 FEB 2026 to 20 APR 2026"),
                Map.entry("currency", "MYR"),
                Map.entry("accountHolder", "BAGUS MAHENDRA WICAKSONO"),
                Map.entry("transaction1", "15 FEB 2026#GROCERY - SUPERMART PJ#-350.00"),
                Map.entry("transaction2", "27 FEB 2026#SALARY - HOLYCOW SDN BHD#+14,147.65"),
                Map.entry("transaction3", "10 MAR 2026#RENOVATION ADVANCE - BUILD THE SKY#-5,000.00"),
                Map.entry("transaction4", "28 MAR 2026#SALARY - HOLYCOW SDN BHD#+14,147.65"),
                Map.entry("transaction5", "05 APR 2026#INSURANCE - PRUDENTIAL#-450.00"),
                Map.entry("transaction6", "12 APR 2026#ONLINE TRANSFER - HOME AWESOME SDN BHD (DEPOSIT)#-10,000.00"),
                Map.entry("transaction7", "20 APR 2026#SALARY - HOLYCOW SDN BHD#+14,147.65")
        );

        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-2002", "applicantName", "BAGUS MAHENDRA WICAKSONO"),
                List.of(new DynamicDocumentData("BANK_STATEMENT", bankStatementData))
        );

        String mockLlmJson = """
                {
                  "applicationName": "BAGUS MAHENDRA WICAKSONO",
                  "payslipEmployer": null,
                  "payslipNetSalary": null,
                  "bankStatementSalarySender": "HOLYCOW SDN BHD",
                  "bankStatementMonthlyDeposit": 14147.65
                }
                """;

        when(mockModels.generateContent(eq("gemini-3.5-flash-lite"), anyString(), isNull())).thenReturn(mockResponse);
        when(mockResponse.text()).thenReturn(mockLlmJson);

        // Act & Assert
        StepVerifier.create(service.normalize(request))
                .assertNext(data -> {
                    assertNotNull(data);
                    assertEquals("BAGUS MAHENDRA WICAKSONO", data.applicationName());
                    assertNull(data.payslipEmployer());
                    assertNull(data.payslipNetSalary());
                    assertEquals("HOLYCOW SDN BHD", data.bankStatementSalarySender());
                    assertEquals(14147.65, data.bankStatementMonthlyDeposit());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should normalize payslip document extracting clean employer name and net salary")
    void testNormalizePayslipDocument() throws Exception {
        // Arrange
        Map<String, Object> payslipData = Map.ofEntries(
                Map.entry("companyName", "HOLYCOW SDN BHD"),
                Map.entry("companyAddress", "9th Floor Wisma Yakin, Jalan Mesjid India, 50100 Kuala Lumpur"),
                Map.entry("documentTitle", "SALARY SLIP - APRIL 2026"),
                Map.entry("employeeName", "Bagus Mahendra Wicaksono"),
                Map.entry("occupation", "Information Technology"),
                Map.entry("position", "Application Developer"),
                Map.entry("natureOfBusiness", "Milk Trading"),
                Map.entry("dateJoined", "15 Apr 2017"),
                Map.entry("lengthOfService", "18 Years"),
                Map.entry("monthlyGrossIncome", "19,600.00"),
                Map.entry("grossSalary", "19,600.00"),
                Map.entry("epfContribution", "2,156.00"),
                Map.entry("socso", "46.35"),
                Map.entry("incomeTaxPcb", "3,250.00"),
                Map.entry("totalDeductions", "5,452.35"),
                Map.entry("netSalary", "RM 14,147.65"),
                Map.entry("annualGrossIncome", "RM 235,200.00"),
                Map.entry("employeeSignatureName", "Bagus Mahendra Wicaksono")
        );

        GraphAnalysisRequest request = new GraphAnalysisRequest(
                Map.of("applicationId", "APP-2003", "applicantName", "Bagus Mahendra Wicaksono"),
                List.of(new DynamicDocumentData("PAYSLIP", payslipData))
        );

        String mockLlmJson = """
                {
                  "applicationName": "Bagus Mahendra Wicaksono",
                  "payslipEmployer": "HOLYCOW SDN BHD",
                  "payslipNetSalary": 14147.65,
                  "bankStatementSalarySender": null,
                  "bankStatementMonthlyDeposit": null
                }
                """;

        when(mockModels.generateContent(eq("gemini-3.5-flash-lite"), anyString(), isNull())).thenReturn(mockResponse);
        when(mockResponse.text()).thenReturn(mockLlmJson);

        // Act & Assert
        StepVerifier.create(service.normalize(request))
                .assertNext(data -> {
                    assertNotNull(data);
                    assertEquals("Bagus Mahendra Wicaksono", data.applicationName());
                    assertEquals("HOLYCOW SDN BHD", data.payslipEmployer());
                    assertEquals(14147.65, data.payslipNetSalary());
                    assertNull(data.bankStatementSalarySender());
                    assertNull(data.bankStatementMonthlyDeposit());
                })
                .verifyComplete();
    }
}
