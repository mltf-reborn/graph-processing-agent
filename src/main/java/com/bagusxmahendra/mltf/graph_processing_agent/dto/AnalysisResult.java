package com.bagusxmahendra.mltf.graph_processing_agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Final structured evaluation response indicating fraud triangulation results.
 */
public record AnalysisResult(
    @JsonProperty("status")
    String status,

    @JsonProperty("checkName")
    String checkName,

    @JsonProperty("passed")
    boolean passed,

    @JsonProperty("discrepancies")
    List<String> discrepancies
) {
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_FLAGGED = "FLAGGED";
    public static final String CHECK_SALARY_TRIANGULATION = "SALARY_TRIANGULATION";

    public static AnalysisResult approved(List<String> discrepancies) {
        return new AnalysisResult(STATUS_APPROVED, CHECK_SALARY_TRIANGULATION, true, discrepancies != null ? discrepancies : List.of());
    }

    public static AnalysisResult flagged(List<String> discrepancies) {
        return new AnalysisResult(STATUS_FLAGGED, CHECK_SALARY_TRIANGULATION, false, discrepancies != null ? discrepancies : List.of());
    }
}
