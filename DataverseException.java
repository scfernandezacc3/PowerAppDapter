/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 * Exception thrown when the Dataverse Web API returns a non-success 
 * status code or a business logic error.
 * @author stephen
 */
package powerappdapter;

public class DataverseException extends Exception {
    private final int statusCode;

    public DataverseException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

