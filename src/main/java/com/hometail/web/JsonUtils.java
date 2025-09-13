package com.hometail.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * A utility class providing centralized configuration for JSON processing
 * using Jackson's ObjectMapper. This class ensures consistent JSON handling
 * throughout the application, particularly for Java 8 date/time types.
 * 
 * <p>The ObjectMapper instance provided by this class is pre-configured with:
 * <ul>
 *   <li>Java 8 date/time support via JavaTimeModule</li>
 *   <li>ISO-8601 date formatting (disables WRITE_DATES_AS_TIMESTAMPS)</li>
 * </ul>
 * 
 * <p>This class follows the singleton pattern to ensure all JSON processing
 * uses the same ObjectMapper instance, which is both thread-safe and more efficient.
 * 
 * <p><b>Example usage:</b></p>
 * <pre>
 * // Convert object to JSON string
 * String json = JsonUtils.getObjectMapper().writeValueAsString(myObject);
 * 
 * // Convert JSON string to object
 * MyType obj = JsonUtils.getObjectMapper().readValue(jsonString, MyType.class);
 * </pre>
 * 
 * @see com.fasterxml.jackson.databind.ObjectMapper
 * @see com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
 */
public final class JsonUtils {
    
    // Thread-safe ObjectMapper instance with Java 8 date/time support
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    // Private constructor to prevent instantiation
    private JsonUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Creates and configures a new ObjectMapper instance with Java 8 date/time support
     * and proper date formatting.
     * 
     * @return a new, configured ObjectMapper instance
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register Java 8 date/time module for proper handling of LocalDate, LocalDateTime, etc.
        mapper.registerModule(new JavaTimeModule());
        // Use ISO-8601 date format instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    /**
     * Returns the shared, pre-configured ObjectMapper instance.
     * This instance is thread-safe and should be used for all JSON processing
     * to ensure consistent behavior throughout the application.
     * 
     * @return the shared ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}