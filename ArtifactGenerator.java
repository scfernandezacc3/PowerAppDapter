/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package powerappdapter;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Responsible for writing local filesystem artifacts based on the manifest.
 * @author stephen
 */
public class ArtifactGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ArtifactGenerator.class);

    /**
     * Write the output files needed to define the Power Platform application
     * @param logic JSON logic content
     * @param automation JSON automation content
     * @param ui JSON UI content
     * @throws IOException 
     */
    public void generate(JsonNode logic, JsonNode automation, JsonNode ui) throws IOException {
        logger.info("event=artifact_generation_start");
        Files.write(Paths.get("app_logic.pfx"), logic.get("power_fx").toString().getBytes());
        Files.write(Paths.get("business_logic.js"), logic.get("javascript").toString().getBytes());
        Files.write(Paths.get("flow_blueprints.txt"), automation.toString().getBytes());
        Files.write(Paths.get("ui_layout_spec.json"), ui.toString().getBytes());
        logger.info("event=artifact_generation_success");
    }
}
