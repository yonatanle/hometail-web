package com.hometail.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Data Transfer Object representing an animal breed in the system.
 * This class is used to transfer breed data between different layers of the application,
 * particularly in the context of animal categorization and management.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreedDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Unique identifier for the breed */
    private Long id;
    
    /** Name of the breed (e.g., "Labrador Retriever", "Persian") */
    private String name;
    
    /** ID of the category this breed belongs to */
    private Long categoryId;
    
    /** Name of the category this breed belongs to (e.g., "Dogs", "Cats") */
    private String categoryName;

    /** 
     * Flag indicating if the breed is active and should be visible in the system.
     * Inactive breeds are typically hidden from selection but kept for reference.
     */
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    /** 
     * Numeric value used for sorting breeds in the user interface.
     * Lower values appear first when breeds are displayed in a sorted list.
     */
    private Integer sortOrder;
}
