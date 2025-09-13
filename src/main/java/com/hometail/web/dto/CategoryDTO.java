package com.hometail.web.dto;

import java.io.Serializable;

/**
 * Data Transfer Object representing an animal category in the system.
 * This class is used to transfer category data between different layers of the application,
 * particularly for organizing and managing different types of animals (e.g., Dogs, Cats).
 *
 */
public class CategoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /** Unique identifier for the category */
    private Long id;
    
    /** 
     * Name of the category (e.g., "Dogs", "Cats", "Birds").
     * Should be unique within the system.
     */
    private String name;
    
    /** 
     * Flag indicating if the category is active in the system.
     * Inactive categories are typically hidden from selection but kept for reference.
     */
    private boolean active = true;
    
    /** 
     * Numeric value used for sorting categories in the user interface.
     * Lower values appear first when categories are displayed in a sorted list.
     */
    private Integer sortOrder;

    /**
     * Gets the unique identifier of the category.
     *
     * @return the category ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the category.
     *
     * @param id the category ID to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the name of the category.
     *
     * @return the category name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the category.
     *
     * @param name the category name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Checks if the category is active.
     *
     * @return true if the category is active, false otherwise
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the active status of the category.
     *
     * @param active true to mark the category as active, false otherwise
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Gets the sort order value for the category.
     *
     * @return the sort order value
     */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /**
     * Sets the sort order value for the category.
     * Lower values will appear first in sorted lists.
     *
     * @param sortOrder the sort order value to set
     */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}