package com.bagusxmahendra.mltf.graph_processing_agent.config;

import com.google.cloud.NoCredentials;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing Google Cloud Spanner DatabaseClient bean.
 */
@Configuration
public class SpannerConfig {

    private static final Logger log = LoggerFactory.getLogger(SpannerConfig.class);

    @Value("${spring.cloud.gcp.project-id:mock-gcp-project}")
    private String projectId;

    @Value("${spring.cloud.gcp.spanner.instance-id:mock-instance}")
    private String instanceId;

    @Value("${spring.cloud.gcp.spanner.database:mock-database}")
    private String databaseId;

    @Value("${spring.cloud.gcp.spanner.emulator-host:}")
    private String emulatorHost;

    @Bean
    @ConditionalOnMissingBean(DatabaseClient.class)
    public DatabaseClient spannerDatabaseClient() {
        log.info("Configuring Spanner DatabaseClient for project='{}', instance='{}', database='{}'",
                projectId, instanceId, databaseId);
        try {
            SpannerOptions.Builder builder = SpannerOptions.newBuilder().setProjectId(projectId);
            if (emulatorHost != null && !emulatorHost.isBlank()) {
                builder.setEmulatorHost(emulatorHost);
            }
            Spanner spanner = builder.build().getService();
            return spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));
        } catch (Exception e) {
            log.warn("Application default credentials not available for Spanner ({}). Initializing with NoCredentials for local/offline execution.", e.getMessage());
            SpannerOptions.Builder builder = SpannerOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(NoCredentials.getInstance());
            if (emulatorHost != null && !emulatorHost.isBlank()) {
                builder.setEmulatorHost(emulatorHost);
            }
            Spanner spanner = builder.build().getService();
            return spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));
        }
    }
}
