package com.bagusxmahendra.mltf.graph_processing_agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured schema target produced by LLM semantic normalization from raw OCR data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StandardizedSalaryData(
    @JsonProperty("applicationName")
    String applicationName,

    @JsonProperty("payslipEmployer")
    String payslipEmployer,

    @JsonProperty("payslipNetSalary")
    Double payslipNetSalary,

    @JsonProperty("bankStatementSalarySender")
    String bankStatementSalarySender,

    @JsonProperty("bankStatementMonthlyDeposit")
    Double bankStatementMonthlyDeposit
) {}
