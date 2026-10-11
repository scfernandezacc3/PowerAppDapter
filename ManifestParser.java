package powerappdapter;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for reading and validating the JSON manifest structure.
 * Transforms raw JSON into strongly-typed Domain Models.
 * * @author stephen
 */
public class ManifestParser {
    private static final Logger logger = LoggerFactory.getLogger(ManifestParser.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses the JSON file and performs structural validation.
     * @param path File path to the manifest.
     * @return A fully hydrated ManifestModel.
     * @throws ManifestException if the file is missing or invalid.
     */
    public ManifestModel parse(String path) throws ManifestException {
        logger.info("event=manifest_parse_start path={}", path);
        File file = new File(path);
        
        // avoid issues with overwrites
        if (!file.exists()) {
            throw new ManifestException("Manifest file not found: " + path);
        }

        try {
            JsonNode root = mapper.readTree(file);
            validateStructure(root);

            List<EntityDefinition> entities = new ArrayList<>();
            JsonNode entitiesNode = root.get("data_model").get("entities");

            // process the JSON structure
            for (int i = 0; i < entitiesNode.size(); i++) {
                JsonNode entNode = entitiesNode.get(i);
                List<AttributeDefinition> attrs = new ArrayList<>();
                
                for (int j = 0; j < entNode.get("attributes").size(); j++) {
                    JsonNode attrNode = entNode.get("attributes").get(j);
                    String type = attrNode.has("type") ? attrNode.get("type").asText() : "String";
                    String target = attrNode.has("lookup_target_entity") ? attrNode.get("lookup_target_entity").asText() : null;
                    
                    // Logic: The first attribute in the array is treated as the Primary Name column
                    boolean isPrimary = (j == 0);

                    attrs.add(new AttributeDefinition(
                        attrNode.get("name").asText(),
                        type,
                        attrNode.has("display_name") ? attrNode.get("display_name").asText() : attrNode.get("name").asText(),
                        target,
                        isPrimary
                    ));
                }
                entities.add(new EntityDefinition(
                    entNode.get("name").asText(),
                    entNode.has("display_name") ? entNode.get("display_name").asText() : entNode.get("name").asText(),
                    attrs
                ));
            }

            logger.info("event=manifest_parse_success count={}", entities.size());
            return new ManifestModel(entities, root.get("logic_manifest"), root.get("automation_manifest"), root.get("ui_manifest"));

        } catch (IOException e) {
            throw new ManifestException("Failed to read JSON: " + e.getMessage());
        }
    }

    /**
     * Checks for the required JSON elements
     * @param root tree root
     * @throws ManifestException 
     */
    private void validateStructure(JsonNode root) throws ManifestException {
        String[] required = {"data_model", "logic_manifest", "automation_manifest", "ui_manifest"};
        for (String field : required) {
            if (!root.has(field)) throw new ManifestException("Missing required section: " + field);
        }
    }
}
