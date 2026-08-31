package com.bagusxmahendra.mltf.graph_processing_agent.rule;

/**
 * Context payload passed to rules during evaluation containing both application metadata,
 * Spanner Graph properties, and LLM-normalized document values.
 */
public record TriangulationContext(
    String applicationId,
    String applicationApplicantName,
    String documentApplicantName,
    String declaredEmployer,
    Double declaredSalary,
    String actualSender,
    Double actualDeposit
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String applicationId;
        private String applicationApplicantName;
        private String documentApplicantName;
        private String declaredEmployer;
        private Double declaredSalary = 0.0;
        private String actualSender;
        private Double actualDeposit = 0.0;

        public Builder applicationId(String applicationId) {
            this.applicationId = applicationId;
            return this;
        }

        public Builder applicationApplicantName(String applicationApplicantName) {
            this.applicationApplicantName = applicationApplicantName;
            return this;
        }

        public Builder documentApplicantName(String documentApplicantName) {
            this.documentApplicantName = documentApplicantName;
            return this;
        }

        public Builder declaredEmployer(String declaredEmployer) {
            this.declaredEmployer = declaredEmployer;
            return this;
        }

        public Builder declaredSalary(Double declaredSalary) {
            this.declaredSalary = declaredSalary != null ? declaredSalary : 0.0;
            return this;
        }

        public Builder actualSender(String actualSender) {
            this.actualSender = actualSender;
            return this;
        }

        public Builder actualDeposit(Double actualDeposit) {
            this.actualDeposit = actualDeposit != null ? actualDeposit : 0.0;
            return this;
        }

        public TriangulationContext build() {
            return new TriangulationContext(
                applicationId,
                applicationApplicantName,
                documentApplicantName,
                declaredEmployer,
                declaredSalary,
                actualSender,
                actualDeposit
            );
        }
    }
}
