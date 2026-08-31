package com.bagusxmahendra.mltf.graph_processing_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Dynamic document payload containing OCR or semi-structured extracted data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DynamicDocumentData(
    @NotBlank(message = "documentType must not be blank")
    @JsonProperty("documentType")
    String documentType,

    @NotNull(message = "extractedData must not be null")
    @JsonProperty("extractedData")
    Map<String, Object> extractedData
) {}
