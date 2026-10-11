/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package powerappdapter;

/**
 * Exception thrown when the input JSON manifest is structurally 
 * invalid or missing required deployment sections.
 */
public class ManifestException extends Exception {
    public ManifestException(String message) {
        super(message);
    }
}
