/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package powerappdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Stack;

/**
 * Core service for interacting with the Dataverse Web API.
 * Implements:
 * 1. Metadata-correct JSON payloads (@odata.type, Prefixing, PrimaryName).
 * 2. Exponential Backoff for HTTP 429 (Throttling).
 * 3. Transactional Rollback via resource URI tracking.
 */
public class DataverseClient {
    private static final Logger logger = LoggerFactory.getLogger(DataverseClient.class);
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 2000;

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String envUrl;
    private final String prefix;
    private String accessToken;

    /**
     * Tracks the URIs of successfully created resources to allow for rollback.
     */
    private final Stack<String> createdResourceStack = new Stack<>();

    public DataverseClient(HttpClient httpClient, String tenantId, String clientId, String clientSecret, String envUrl, String prefix) {
        this.httpClient = httpClient;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.envUrl = envUrl.replace("https://", "");
        this.prefix = prefix;
    }

    /**
     * Performs OAuth2 Client Credentials flow to acquire access token.
     */
    public void authenticate() throws Exception {
        logger.info("event=auth_start tenant={}", tenantId);
        String url = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantId);
        String form = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s&scope=https://%s/.default",
                clientId, clientSecret, envUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            this.accessToken = mapper.readTree(response.body()).get("access_token").asText();
            logger.info("event=auth_success");
        } else {
            throw new DataverseException("Authentication failed", response.statusCode());
        }
    }

    /**
     * Validates existence of all resources before deployment (Pre-flight check).
     */
    public void validateAll(List<EntityDefinition> entities) throws Exception {
        logger.info("event=validation_phase_start total_entities={}", entities.size());
        for (EntityDefinition entity : entities) {
            checkResourceExistence(entity.getName(), null);
            for (AttributeDefinition attr : entity.getAttributes()) {
                checkResourceExistence(entity.getName(), attr.getName());
            }
        }
        logger.info("event=validation_phase_success");
    }

    /**
     * Deploys a single entity and its attributes.
     */
    public void deployEntity(EntityDefinition entity) throws Exception {
        ensureTableExists(entity);
        for (AttributeDefinition attr : entity.getAttributes()) {
            ensureColumnExists(entity.getName(), attr);
        }
        logger.info("event=entity_deployed name={}", entity.getName());
    }

    /**
     * Reverses all successful creations in the event of a failure.
     */
    public void rollback() {
        logger.warn("event=rollback_initiated total_resources_to_remove={}", createdResourceStack.size());
        while (!createdResourceStack.isEmpty()) {
            String uri = createdResourceStack.pop();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uri))
                        .header("Authorization", "Bearer " + accessToken)
                        .DELETE().build();
                
                // We don't retry deletes; if they fail, we log and continue cleaning up
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("event=resource_removed uri={}", uri);
            } catch (Exception e) {
                logger.error("event=rollback_error uri={} error='{}'", uri, e.getMessage());
            }
        }
        logger.info("event=rollback_complete");
    }

    /**
     * Checks for prior existence of an element before adding it to the Dataverse
     * @param entityName Element name
     * @param attrName Attribute name
     * @throws Exception 
     */
    private void checkResourceExistence(String entityName, String attrName) throws Exception {
        String endpoint = (attrName == null) 
            ? "https://" + envUrl + "/api/data/v9.2/EntityDefinitions(" + prefix + entityName + ")"
            : "https://" + envUrl + "/api/data/v9.2/EntityDefinitions(" + prefix + entityName + ")/Attributes(" + prefix + attrName + ")";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();

        HttpResponse<String> response = sendWithRetry(request);
        if (response.statusCode() != 200 && response.statusCode() != 404) {
            throw new DataverseException("Resource check failed: " + endpoint, response.statusCode());
        }
    }

    /**
     * Verifies table exists in the Dataverse
     * @param entity Entity to check for
     * @throws Exception 
     */
    private void ensureTableExists(EntityDefinition entity) throws Exception {
        String endpoint = "https://" + envUrl + "/api/data/v9.2/EntityDefinitions(" + prefix + entity.getName() + ")";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessToken).GET().build();
        HttpResponse<String> res = sendWithRetry(request);

        if (res.statusCode() == 404) {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("@odata.type", "Microsoft.Dynamics.CRM.Entity");
            payload.put("SchemaName", prefix + entity.getName());
            payload.set("DisplayName", mapper.createObjectNode().set("LocalizedLabels", 
                mapper.createArrayNode().add(mapper.createObjectNode().put("Label", entity.getDisplayName()).put("LanguageCode", 1033))));
            
            // Fix #3: Embed Primary Name Attribute Metadata inside Table Creation
            ArrayNode attributesArray = mapper.createArrayNode();
            for (AttributeDefinition attr : entity.getAttributes()) {
                if (attr.isPrimary()) {
                    ObjectNode primaryAttr = mapper.createObjectNode();
                    primaryAttr.put("@odata.type", "Microsoft.Dynamics.CRM.StringAttributeMetadata");
                    primaryAttr.put("SchemaName", prefix + attr.getName());
                    primaryAttr.put("IsPrimaryName", true);
                    primaryAttr.put("DisplayName", attr.getDisplayName());
                    primaryAttr.set("LocalizedLabels", mapper.createArrayNode().add(mapper.createObjectNode().put("Label", attr.getDisplayName()).put("LanguageCode", 1033)));
                    attributesArray.add(primaryAttr);
                }
            }
            payload.set("Attributes", attributesArray);

            executePost("https://" + envUrl + "/api/data/v9.2/EntityDefinitions", payload, "Table:" + entity.getName());
        }
    }

    /** 
     * Checks that column exists in table defined in Dataverse
     * @param tableName table name
     * @param attr attribute (column)
     * @throws Exception 
     */
    private void ensureColumnExists(String tableName, AttributeDefinition attr) throws Exception {
        String endpoint = "https://" + envUrl + "/api/data/v9.2/EntityDefinitions(" + prefix + tableName + ")/Attributes(" + prefix + attr.getName() + ")";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessToken).GET().build();
        HttpResponse<String> res = sendWithRetry(request);

        if (res.statusCode() == 404) {
            String postEndpoint = "https://" + envUrl + "/api/data/v9.2/EntityDefinitions(" + prefix + tableName + ")/Attributes";
            ObjectNode payload = mapper.createObjectNode();
            
            // Fix #4: Correct @odata.type and Metadata constraints
            payload.put("@odata.type", getOdataType(attr.getType()));
            payload.put("SchemaName", prefix + attr.getName());
            payload.set("DisplayName", mapper.createObjectNode().set("LocalizedLabels", 
                mapper.createArrayNode().add(mapper.createObjectNode().put("Label", attr.getDisplayName()).put("LanguageCode", 1033))));

            if ("String".equalsIgnoreCase(attr.getType())) {
                payload.put("MaxLength", 200);
            }

            if ("Lookup".equalsIgnoreCase(attr.getType()) && attr.getLookupTargetEntity() != null) {
                ObjectNode lookupAttr = mapper.createObjectNode();
                lookupAttr.put("TargetEntity", prefix + attr.getLookupTargetEntity());
                payload.set("LookupAttribute", lookupAttr);
            }

            executePost(postEndpoint, payload, "Column:" + attr.getName());
        }
    }

    /**
     * POST data to endpoint with necessary credentials and markup
     * @param endpoint URL for Dataverse 
     * @param payload content to add
     * @param context insertion point
     * @throws Exception 
     */
    private void executePost(String endpoint, ObjectNode payload, String context) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        
        HttpResponse<String> response = sendWithRetry(request);
        if (response.statusCode() != 201 && response.statusCode() != 204) {
            throw new DataverseException("Post failed for " + context, response.statusCode());
        }
        
        // Track the URI for rollback purposes
        String entityId = response.headers().firstValue("OData-EntityId").orElse(endpoint);
        createdResourceStack.push(entityId);
        
        logger.info("event=resource_created context={}", context);
    }

    /**
     * Implements Exponential Backoff with Retry-After header awareness.
     * @param HttpRequest request to make with controlled retry
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        int attempt = 0;
        while (true) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429 && attempt < MAX_RETRIES) {
                attempt++;
                long delay = calculateBackoff(response, attempt);
                logger.warn("event=throttling_detected attempt={} delay_ms={}", attempt, delay);
                Thread.sleep(delay);
                continue;
            }
            return response;
        }
    }

    private long calculateBackoff(HttpResponse<String> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(s -> {
                    try { return Long.parseLong(s) * 1000L; } 
                    catch (NumberFormatException e) { return (long) Math.pow(2, attempt) * INITIAL_BACKOFF_MS; }
                })
                .orElse((long) Math.pow(2, attempt) * INITIAL_BACKOFF_MS);
    }

    /**
     * Obtain Dataverse data type reference for element type
     * @param type String type name
     * @return Odata type corresponding
     */
    private String getOdataType(String type) {
        switch (type.toLowerCase()) {
            case "integer": return "Microsoft.Dynamics.CRM.IntegerAttributeMetadata";
            case "boolean": return "Microsoft.Dynamics.CRM.BooleanAttributeMetadata";
            case "lookup":  return "Microsoft.Dynamics.CRM.LookupAttributeMetadata";
            default:        return "Microsoft.Dynamics.CRM.StringAttributeMetadata";
        }
    }
}
