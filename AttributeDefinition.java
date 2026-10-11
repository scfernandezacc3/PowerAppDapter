/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package powerappdapter;

/**
 * Represents a Dataverse Column metadata definition.
 */
public class AttributeDefinition {
    private final String name;
    private final String type; 
    private final String displayName;
    private final String lookupTargetEntity;
    private final boolean isPrimary;

    public AttributeDefinition(String name, String type, String displayName, String lookupTargetEntity, boolean isPrimary) {
        this.name = name;
        this.type = type;
        this.displayName = displayName;
        this.lookupTargetEntity = lookupTargetEntity;
        this.isPrimary = isPrimary;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getDisplayName() { return displayName; }
    public String getLookupTargetEntity() { return lookupTargetEntity; }
    public boolean isPrimary() { return isPrimary; }
}
