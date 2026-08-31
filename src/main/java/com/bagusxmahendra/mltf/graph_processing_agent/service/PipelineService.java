package com.bagusxmahendra.mltf.graph_processing_agent.service;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.AnalysisResult;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.GraphAnalysisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Reactive orchestration service orchestrating the multi-stage document processing pipeline:
 * 1. Semantic normalization with Gemini LLM
 * 2. Fraud ring & salary triangulation via Spanner Graph (ISO GQL)
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final LlmNormalizationService llmNormalizationService;
    private final SpannerGraphService spannerGraphService;

    public PipelineService(
            LlmNormalizationService llmNormalizationService,
            SpannerGraphService spannerGraphService) {
        this.llmNormalizationService = llmNormalizationService;
        this.spannerGraphService = spannerGraphService;
    }

    /**
     * Orchestrates the reactive document analysis pipeline.
     *
     * @param request the incoming dynamic graph analysis request
     * @return Mono emitting the final AnalysisResult
     */
    public Mono<AnalysisResult> processSalaryAnalysis(GraphAnalysisRequest request) {
        log.info("Initiating salary analysis pipeline for request: {}", request.extractApplicationId());

        return llmNormalizationService.normalize(request)
                .flatMap(standardizedData -> {
                    String applicationId = request.extractApplicationId();
                    String applicantName = request.extractApplicantName();
                    log.info("LLM normalization succeeded for applicationId='{}'. Proceeding to Spanner Graph evaluation.", applicationId);
                    return spannerGraphService.evaluateSalaryDiscrepancies(applicationId, applicantName, standardizedData)
                            .flatMap(analysisResult ->
                                    spannerGraphService.saveApplicationGraphData(
                                            applicationId,
                                            applicantName,
                                            analysisResult.status(),
                                            standardizedData
                                    ).thenReturn(analysisResult)
                            );
                })
                .doOnSuccess(result -> log.info("Pipeline execution completed successfully with status: {}", result.status()))
                .doOnError(error -> log.error("Pipeline execution failed: {}", error.getMessage(), error));
    }
}
