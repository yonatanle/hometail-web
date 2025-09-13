package com.hometail.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Data Transfer Object representing an animal in the system.
 * This class is used to transfer animal data between different layers of the application,
 * including communication between frontend and backend.
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnimalDTO implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique identifier for the animal */
    private Long id;
    
    /** Name of the animal */
    private String name;

    // Category information
    /** ID of the animal's category */
    private Long categoryId;
    
    /** Name of the animal's category (e.g., "Dog", "Cat") */
    private String category;
    
    /** Display name of the animal's category */
    private String categoryName;

    // Breed information
    /** ID of the animal's breed */
    private Long breedId;
    
    /** Name of the animal's breed (e.g., "Labrador", "Persian") */
    private String breedName;
    
    /** 
     * @deprecated Use breedName instead. This field is kept for backward compatibility.
     */
    private String breed;

    /** Gender of the animal (e.g., "Male", "Female") */
    private String gender;
    
    /** Size category of the animal (e.g., "Small", "Medium", "Large", "Extra Large") */
    private String size;

    // Description fields
    /** Short description of the animal (for listings and previews) */
    private String shortDescription;
    
    /** Detailed description of the animal */
    private String longDescription;
    
    /** Flag indicating if the animal has been adopted */
    private boolean adopted;
    
    /** URL or relative path to the animal's profile image */
    private String image;

    // Owner information
    /** ID of the animal's current or previous owner */
    private Long ownerId;
    
    /** Name of the animal's owner */
    private String ownerName;
    
    /** Email address of the animal's owner */
    private String ownerEmail;
    
    /** Contact phone number of the animal's owner */
    private String ownerPhone;

    // Date of birth information
    /** Animal's date of birth in yyyy-MM-dd format */
    @JsonFormat(pattern = "yyyy-MM-dd", shape = JsonFormat.Shape.STRING)
    private LocalDate birthday;
    
    /** Human-readable description of the animal's age (e.g., "2 years old") */
    private String ageDescription;
}
