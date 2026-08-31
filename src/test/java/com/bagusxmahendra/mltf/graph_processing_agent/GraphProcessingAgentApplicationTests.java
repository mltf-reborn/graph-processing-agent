package com.bagusxmahendra.mltf.graph_processing_agent;

import com.google.cloud.spanner.DatabaseClient;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class GraphProcessingAgentApplicationTests {

    @MockitoBean
    private DatabaseClient databaseClient;

    @MockitoBean
    private Client geminiClient;

    @Test
    void contextLoads() {
    }

}
