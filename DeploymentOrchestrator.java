/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package powerappdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * High-level coordinator of the deployment pipeline.
 * Man	ages the lifecycle: Parse -> Auth -> Validate -> Deploy -> Rollback (if needed).
 * @author stephen
 */
public class DeploymentOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentOrchestrator.class);
    private final DataverseClient dataverseClient;
    private final ManifestParser parser;
    private final ArtifactGenerator generator;
    private final int THREAD_POOL_SIZE = 10;

    public DeploymentOrchestrator(DataverseClient dataverseClient, ManifestParser parser, ArtifactGenerator generator) {
        this.dataverseClient = dataverseClient;
        this.parser = parser;
        this.generator = generator;
    }

    /**
     * Process the manifest using queued parallel execution
     * @param manifestPath manifest location
     * @throws Exception 
     */
    public void execute(String manifestPath) throws Exception {
        logger.info("event=pipeline_start path={}", manifestPath);
        ManifestModel manifest = parser.parse(manifestPath);
        dataverseClient.authenticate();

        // 1. ATOMICITY: Validation Phase (Parallelized)
        logger.info("event=validation_phase_start");
        dataverseClient.validateAll(manifest.getEntities());

        // 2. ATOMICITY: Deployment Phase (Parallelized)
        logger.info("event=deployment_phase_start");
        // define the pool maximum size, to avoid swamping the system
        ExecutorService deployExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<Void>> entityTasks = new ArrayList<>();
            for (EntityDefinition entity : manifest.getEntities()) {
                // execute the list of entities in queued parallel manner
                entityTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        dataverseClient.deployEntity(entity);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, deployExecutor));
            }
            CompletableFuture.allOf(entityTasks.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            // If any entity fails, trigger the rollback of all created resources
            logger.error("event=deployment_phase_failed error='{}'. Triggering rollback...", e.getMessage());
            dataverseClient.rollback();
            throw e;
        } finally {
            shutdownExecutor(deployExecutor);
        }

        // 3. Artifact Generation
        generator.generate(manifest.getLogicManifest(), manifest.getAutomationManifest(), manifest.getUiManifest());
        logger.info("event=pipeline_complete");
    }

    /**
     * Manage controlled closure of the parallel execution
     * @param executor Multi-instance thread executor
     */
    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
