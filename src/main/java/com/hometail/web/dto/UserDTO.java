package com.hometail.web.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Data Transfer Object representing a user in the system.
 * This class is used to transfer user data between different layers of the application,
 * including communication between frontend and backend.
 *
 */
@Data
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique identifier for the user */
    private Long id;
    
    /** User's full name as it should be displayed in the UI */
    private String fullName;
    
    /** 
     * User's email address.
     * Used for communication and as a login identifier.
     */
    private String email;
    
    /** 
     * Unique username for the user.
     * Used for login and display purposes.
     */
    private String username;
    
    /** 
     * User's contact phone number.
     * Format should be consistent across the application.
     */
    private String phoneNumber;
    
    /** 
     * User's role in the system.
     * Determines permissions and access levels.
     * Example values: "ADMIN", "USER"
     */
    private String role;
}
