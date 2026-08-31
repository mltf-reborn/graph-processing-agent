package com.bagusxmahendra.mltf.graph_processing_agent.rule;

import java.util.Optional;

/**
 * Interface contract for pluggable, configurable document triangulation and fraud detection rules.
 */
public interface TriangulationRule {

    /**
     * Unique identifier of the rule (e.g. "RULE_NAME_CONSISTENCY").
     */
    String ruleId();

    /**
     * Human readable name of the rule.
     */
    String ruleName();

    /**
     * Checks if this rule is actively enabled via configuration.
     */
    boolean isEnabled();

    /**
     * Evaluates the triangulation context.
     *
     * @param context the evaluation context containing application and document data
     * @return Optional containing discrepancy message if rule failed, or Optional.empty() if passed
     */
    Optional<String> evaluate(TriangulationContext context);
}
