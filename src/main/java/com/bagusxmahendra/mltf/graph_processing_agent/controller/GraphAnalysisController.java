package com.bagusxmahendra.mltf.graph_processing_agent.controller;

import com.bagusxmahendra.mltf.graph_processing_agent.dto.AnalysisResult;
import com.bagusxmahendra.mltf.graph_processing_agent.dto.GraphAnalysisRequest;
import com.bagusxmahendra.mltf.graph_processing_agent.service.PipelineService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Reactive REST Controller exposing document analysis & fraud triangulation endpoints.
 */
@RestController
@RequestMapping("/api/v1/graph")
public class GraphAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(GraphAnalysisController.class);

    private final PipelineService pipelineService;

    public GraphAnalysisController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Endpoint for ingesting dynamic loan documents, extracting structured salary data via GenAI,
     * and performing ISO GQL Spanner Graph triangulation analysis.
     *
     * @param request the incoming raw loan application payload and OCR documents
     * @return Mono of ResponseEntity containing the AnalysisResult
     */
    @PostMapping(value = "/analysis", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AnalysisResult>> analyzeDocumentGraph(
            @Valid @RequestBody GraphAnalysisRequest request) {
        log.info("Received POST /api/v1/graph/analysis request for applicationId: {}", request.extractApplicationId());

        return pipelineService.processSalaryAnalysis(request)
                .map(ResponseEntity::ok);
    }
}
