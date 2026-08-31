package com.bagusxmahendra.mltf.graph_processing_agent.rule;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 3: Verifies that the difference between declared net salary and actual monthly bank deposit
 * does not exceed the configured variance ratio (e.g. 5% = 0.05).
 */
@Component
public class SalaryVarianceRule implements TriangulationRule {

    private final RuleProperties properties;

    public SalaryVarianceRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String ruleId() {
        return "RULE_SALARY_VARIANCE";
    }

    @Override
    public String ruleName() {
        return "Salary vs Bank Deposit Tolerance Check";
    }

    @Override
    public boolean isEnabled() {
        return properties.salaryVariance() != null && properties.salaryVariance().enabled();
    }

    @Override
    public Optional<String> evaluate(TriangulationContext context) {
        Double declaredSalary = context.declaredSalary();
        Double actualDeposit = context.actualDeposit();

        if (declaredSalary == null || declaredSalary <= 0) {
            return Optional.of("Declared salary must be greater than zero for triangulation analysis.");
        }

        if (actualDeposit == null) {
            actualDeposit = 0.0;
        }

        double maxRatio = properties.salaryVariance().maxVarianceRatio();
        double salaryDiff = Math.abs(declaredSalary - actualDeposit);
        double allowedVariance = declaredSalary * maxRatio;

        if (salaryDiff > allowedVariance) {
            return Optional.of(String.format(
                    "Salary amount mismatch exceeds %.0f%% threshold: Declared net salary $%.2f vs Bank deposit $%.2f (difference: $%.2f, allowable: $%.2f).",
                    maxRatio * 100, declaredSalary, actualDeposit, salaryDiff, allowedVariance
            ));
        }

        return Optional.empty();
    }
}
