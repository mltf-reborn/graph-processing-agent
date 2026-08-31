package com.bagusxmahendra.mltf.graph_processing_agent.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties mapping for configurable fraud and triangulation rules.
 */
@ConfigurationProperties(prefix = "pipeline.rules")
public record RuleProperties(
    @DefaultValue NameMatching nameMatching,
    @DefaultValue EmployerMatching employerMatching,
    @DefaultValue SalaryVariance salaryVariance,
    @DefaultValue MinimumSalary minimumSalary
) {
    public RuleProperties {
        if (nameMatching == null) nameMatching = new NameMatching(true, true);
        if (employerMatching == null) employerMatching = new EmployerMatching(true, true);
        if (salaryVariance == null) salaryVariance = new SalaryVariance(true, 0.05);
        if (minimumSalary == null) minimumSalary = new MinimumSalary(false, 1000.0);
    }

    public record NameMatching(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean ignoreCase
    ) {}

    public record EmployerMatching(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean ignoreCase
    ) {}

    public record SalaryVariance(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0.05") double maxVarianceRatio
    ) {}

    public record MinimumSalary(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("1000.00") double minAmount
    ) {}
}
