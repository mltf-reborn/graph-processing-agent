package com.bagusxmahendra.mltf.graph_processing_agent.rule;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 2: Verifies that the employer name declared on the payslip matches the salary sender on the bank statement.
 */
@Component
public class EmployerMatchingRule implements TriangulationRule {

    private final RuleProperties properties;

    public EmployerMatchingRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String ruleId() {
        return "RULE_EMPLOYER_MATCH";
    }

    @Override
    public String ruleName() {
        return "Employer and Payroll Sender Cross-Verification";
    }

    @Override
    public boolean isEnabled() {
        return properties.employerMatching() != null && properties.employerMatching().enabled();
    }

    @Override
    public Optional<String> evaluate(TriangulationContext context) {
        String declaredEmployer = context.declaredEmployer();
        String actualSender = context.actualSender();

        if (declaredEmployer == null || actualSender == null || declaredEmployer.isBlank() || actualSender.isBlank()) {
            return Optional.of("Missing employer data on payslip or bank statement sender for triangulation.");
        }

        boolean matches;
        if (properties.employerMatching().ignoreCase()) {
            matches = declaredEmployer.trim().equalsIgnoreCase(actualSender.trim());
        } else {
            matches = declaredEmployer.trim().equals(actualSender.trim());
        }

        if (!matches) {
            return Optional.of(String.format(
                    "Employer mismatch: Declared employer '%s' does not match bank statement salary sender '%s'.",
                    declaredEmployer.trim(), actualSender.trim()
            ));
        }

        return Optional.empty();
    }
}
