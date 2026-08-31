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
                You are an expert financial document data extraction AI and semantic parser.
                Analyze the following dynamic OCR and document payload extracted from a loan application.
                
                Your task is to standardize the unstructured, dynamic document data into a strictly valid JSON object conforming to this schema:
                {
                  "applicationName": "Full name of the employee or account holder appearing on the documents (e.g. employee on payslip or account holder on bank statement)",
                  "payslipEmployer": "Official employer or organization name extracted from the payslip/salary certificate",
                  "payslipNetSalary": 5000.00, // numeric float/double representing the take-home or net monthly pay
                  "bankStatementSalarySender": "Entity or organization name sending recurring payroll deposits in bank statements",
                  "bankStatementMonthlyDeposit": 5000.00 // numeric float/double of the recurring payroll deposit
                }
                
                Rules for Standardization:
                1. Return ONLY the raw JSON object without markdown formatting, code fences, or additional text.
                2. Extract and standardize the fields using semantic understanding of the dynamic document OCR (e.g. identify take-home pay, parse currencies into numeric doubles such as "$4,500.50" -> 4500.50).
                3. Extract "applicationName" faithfully from the attached documents (employee/account holder name).
                4. Ensure all JSON field names match EXACTLY.
                
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
