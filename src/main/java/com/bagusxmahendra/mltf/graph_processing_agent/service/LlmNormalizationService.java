package com.bagusxmahendra.mltf.graph_processing_agent.service;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.StandardizedSalaryData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive service responsible for normalizing raw, dynamic document OCR payloads
 * into standardized salary data records using Google Gen AI (Gemini) LLM semantic parsing.
 */
@Service
public class LlmNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(LlmNormalizationService.class);

    private final Client geminiClient;
    private final ObjectMapper objectMapper;
    private final String modelName;

    public LlmNormalizationService(
            Client geminiClient,
            ObjectMapper objectMapper,
            @Value("${google.genai.model:gemini-3.5-flash-lite}") String modelName) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.modelName = modelName;
    }

    /**
     * Normalizes unstructured document data to a strict StandardizedSalaryData record.
     * Offloads the blocking GenAI SDK network call onto Schedulers.boundedElastic().
     *
     * @param request the dynamic ingestion payload
     * @return Mono emitting the normalized StandardizedSalaryData
     */
    public Mono<StandardizedSalaryData> normalize(GraphAnalysisRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Starting LLM semantic normalization for application: {}", request.extractApplicationId());

            // 1. Serialize input request to JSON
            String rawJsonPayload = objectMapper.writeValueAsString(request);

            // 2. Construct the semantic parsing prompt
            String prompt = """
                You are an expert financial document data extraction AI and semantic parser specializing in loan underwriting and income verification.
                Analyze the dynamic OCR and document payload extracted from a loan application (which may include BANK_STATEMENT and/or PAYSLIP documents).
                
                Your task is to standardize the unstructured document data into a strictly valid JSON object conforming to this target schema:
                {
                  "applicationName": "Full name of the employee or account holder appearing on the documents (e.g., employeeName on payslip or accountHolder on bank statement)",
                  "payslipEmployer": "Official employer or organization name extracted from the payslip/salary certificate, or null if absent",
                  "payslipNetSalary": 5000.00, // numeric float/double representing net monthly pay (take-home salary after deductions), or null if absent
                  "bankStatementSalarySender": "Clean organization or company name identified as the recurring payroll/salary depositor from bank transactions, or null if absent",
                  "bankStatementMonthlyDeposit": 5000.00 // numeric float/double of the recurring payroll/salary deposit amount (positive credit inflow), or null if absent
                }
                
                Extraction & Standardization Rules:
                1. Account Holder / Applicant Name:
                   - Extract full name from fields like 'employeeName', 'employeeSignatureName', or 'accountHolder'.
                2. Payslip Data Extraction:
                   - Employer Name: Extract the official company/employer name from 'companyName', 'employerName', or header details.
                   - Net Salary: Extract the net monthly take-home salary from 'netSalary' (or 'grossSalary' minus 'totalDeductions'). Avoid gross income or annual income.
                   - Clean currency symbols (e.g., 'RM 14,147.65', '$5,000.00') into a clean numeric double (14147.65).
                3. Bank Statement Transaction Extraction:
                   - Parse delimited transaction strings (e.g., 'DATE#DESCRIPTION#AMOUNT' like '27 FEB 2026#SALARY - HOLYCOW SDN BHD#+14,147.65').
                   - Filter positive credit inflows (marked with '+' or positive values). Exclude debit/expense transactions (marked with '-').
                   - Identify payroll transactions via keywords (SALARY, PAYROLL, GAJI, REMUNERATION, DIRECT DEP, etc.).
                   - Isolate clean company name by removing transaction codes (e.g., from 'SALARY - HOLYCOW SDN BHD', extract 'HOLYCOW SDN BHD').
                   - Convert currency strings into clean numeric float/double values.
                4. Formatting Constraints:
                   - Return ONLY raw JSON without markdown code fences, backticks, or conversational text.
                   - Ensure all JSON field names match the schema EXACTLY.
                
                Input Document Payload:
                %s
                """.formatted(rawJsonPayload);

            // 3. Call Gemini via Google Gen AI Java SDK
            log.debug("Invoking Google Gen AI model '{}'", modelName);
            GenerateContentResponse response = geminiClient.models.generateContent(
                    modelName,
                    prompt,
                    null
            );

            String responseText = response.text();
            if (responseText == null || responseText.isBlank()) {
                throw new IllegalStateException("Received empty response from Google Gen AI model.");
            }

            log.debug("LLM Normalization raw response: {}", responseText);

            // 4. Sanitize potential markdown code fences from LLM output
            String sanitizedJson = sanitizeJson(responseText);

            // 5. Parse response JSON into StandardizedSalaryData record
            StandardizedSalaryData normalizedData = objectMapper.readValue(sanitizedJson, StandardizedSalaryData.class);
            log.info("Successfully normalized salary data: {}", normalizedData);

            return normalizedData;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Cleans code markdown blocks (```json ... ```) from LLM output before Jackson parsing.
     */
    private String sanitizeJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
