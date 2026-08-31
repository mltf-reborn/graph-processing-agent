package com.bagusxmahendra.mltf.graph_processing_agent.rule;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 1: Verifies that the applicant name matches across application metadata and submitted documents.
 */
@Component
public class ApplicantNameMatchingRule implements TriangulationRule {

    private final RuleProperties properties;

    public ApplicantNameMatchingRule(RuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String ruleId() {
        return "RULE_NAME_CONSISTENCY";
    }

    @Override
    public String ruleName() {
        return "Applicant Name Consistency Check";
    }

    @Override
    public boolean isEnabled() {
        return properties.nameMatching() != null && properties.nameMatching().enabled();
    }

    @Override
    public Optional<String> evaluate(TriangulationContext context) {
        String appName = context.applicationApplicantName();
        String docName = context.documentApplicantName();

        if (appName == null || docName == null || appName.isBlank() || docName.isBlank()) {
            return Optional.empty(); // Not enough data for name triangulation
        }

        boolean matches;
        if (properties.nameMatching().ignoreCase()) {
            matches = appName.trim().equalsIgnoreCase(docName.trim());
        } else {
            matches = appName.trim().equals(docName.trim());
        }

        if (!matches) {
            return Optional.of(String.format(
                    "Name mismatch: Declared applicant name '%s' does not match document name '%s'.",
                    appName.trim(), docName.trim()
            ));
        }

        return Optional.empty();
    }
}
