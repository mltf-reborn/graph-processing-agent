package com.bagusxmahendra.mltf.graph_processing_agent.rule;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 4: Verifies that the applicant meets minimum required declared income.
 */
@Component
public class MinimumSalaryRule implements TriangulationRule {

    private final RuleProperties properties;

    public MinimumSalaryRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String ruleId() {
        return "RULE_MINIMUM_SALARY";
    }

    @Override
    public String ruleName() {
        return "Minimum Income Qualification Check";
    }

    @Override
    public boolean isEnabled() {
        return properties.minimumSalary() != null && properties.minimumSalary().enabled();
    }

    @Override
    public Optional<String> evaluate(TriangulationContext context) {
        Double declaredSalary = context.declaredSalary();
        double minRequired = properties.minimumSalary().minAmount();

        if (declaredSalary == null || declaredSalary < minRequired) {
            return Optional.of(String.format(
                    "Income qualification failure: Declared salary $%.2f is below the minimum requirement of $%.2f.",
                    declaredSalary != null ? declaredSalary : 0.0, minRequired
            ));
        }

        return Optional.empty();
    }
}
