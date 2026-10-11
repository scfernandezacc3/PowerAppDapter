package powerappdapter;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 * A strongly-typed container for the parsed deployment manifest.
 * @author stephen
 */
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public class ManifestModel {
    private final List<EntityDefinition> entities;
    private final JsonNode logicManifest;
    private final JsonNode automationManifest;
    private final JsonNode uiManifest;

    public ManifestModel(List<EntityDefinition> entities, JsonNode logic, JsonNode automation, JsonNode ui) {
        this.entities = entities;
        this.logicManifest = logic;
        this.automationManifest = automation;
        this.uiManifest = ui;
    }

    public List<EntityDefinition> getEntities() { return entities; }
    public JsonNode getLogicManifest() { return logicManifest; }
    public JsonNode getAutomationManifest() { return automationManifest; }
    public JsonNode getUiManifest() { return uiManifest; }
}
