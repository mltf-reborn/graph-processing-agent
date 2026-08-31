package com.bagusxmahendra.mltf.graph_processing_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Ingestion request payload carrying dynamic loan application data and attached OCR documents.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphAnalysisRequest(
    @NotNull(message = "loanApplication metadata must not be null")
    @JsonProperty("loanApplication")
    Map<String, Object> loanApplication,

    @NotEmpty(message = "documents list must not be empty")
    @Valid
    @JsonProperty("documents")
    List<DynamicDocumentData> documents
) {
    /**
     * Helper to safely extract or derive an applicationId from the loanApplication map.
     */
    public String extractApplicationId() {
        if (loanApplication != null) {
            Object id = loanApplication.get("applicationId");
            if (id == null) {
                id = loanApplication.get("id");
            }
            if (id != null) {
                return id.toString();
            }
        }
        return "APP-MOCK-" + System.currentTimeMillis();
    }

    /**
     * Helper to safely extract applicantName from the loanApplication map.
     */
    public String extractApplicantName() {
        if (loanApplication != null) {
            Object name = loanApplication.get("applicantName");
            if (name == null) {
                name = loanApplication.get("name");
            }
            if (name == null) {
                name = loanApplication.get("applicant");
            }
            if (name != null) {
                return name.toString();
            }
        }
        return null;
    }
}
