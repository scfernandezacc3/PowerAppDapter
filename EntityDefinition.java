/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package powerappdapter;

import java.util.List;

/**
 * Represents a Dataverse Table metadata definition.
 * @author stephen
 */
public class EntityDefinition {
    private final String name;
    private final String displayName;
    private final List<AttributeDefinition> attributes;

    public EntityDefinition(String name, String displayName, List<AttributeDefinition> attributes) {
        this.name = name;
        this.displayName = displayName;
        this.attributes = attributes;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public List<AttributeDefinition> getAttributes() { return attributes; }
}
