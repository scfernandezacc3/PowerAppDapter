/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package powerappdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.UUID;

/**
 * Main entry point for the PowerAppDapter application.
 * Initializes the tracing context and orchestrates the deployment.
 * @author stephen
 */
public class PowerAppDapter {
    private static final Logger logger = LoggerFactory.getLogger(PowerAppDapter.class);

    public static void main(String[] args) {
        // Item 5: Distributed Tracing - Generate a unique correlation ID for this deployment run
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            // Item 1: Securely fetch from Environment Variables
            String tenantId = System.getenv("AZURE_TENANT_ID");
            String clientId = System.getenv("AZURE_CLIENT_ID");
            String clientSecret = System.getenv("AZURE_CLIENT_SECRET");
            String envUrl = System.getenv("DATAVERSE_ENV_URL");
            String prefix = System.getenv().getOrDefault("DATAVERSE_PREFIX", "new_");

            // Abandon processing if we are missing an environment variable
            if (tenantId == null) {
                logger.error("event=config_error message='Missing required environment variable [AZURE_TENANT_ID]'");
                System.exit(1);
            }
            
            if (clientId == null) {
                logger.error("event=config_error message='Missing required environment variable [AZURE_CLIENT_ID]'");
                System.exit(1);
            }

            if (clientSecret == null) {
                logger.error("event=config_error message='Missing required environment variable [AZURE_CLIENT_SECRET]'");
                System.exit(1);
            }

            if (envUrl == null) {
                logger.error("event=config_error message='Missing required environment variable [DATAVERSE_ENV_URL]'");
                System.exit(1);
            }            

            // create the structure elements
            HttpClient httpClient = HttpClient.newHttpClient();
            DataverseClient client = new DataverseClient(httpClient, tenantId, clientId, clientSecret, envUrl, prefix);
            ManifestParser parser = new ManifestParser();
            ArtifactGenerator generator = new ArtifactGenerator();
            DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(client, parser, generator);
            
            // ingest the definitions
            orchestrator.execute("platform_manifest.json");

        } catch (Exception e) {
            logger.error("event=pipeline_fatal_error error='{}'", e.getMessage(), e);
            System.exit(1);
        } finally {
            // Clean up the MDC context
            MDC.clear();
        }
    }
}
