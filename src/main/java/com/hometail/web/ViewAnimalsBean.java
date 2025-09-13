package com.hometail.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hometail.web.dto.AnimalDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A view-scoped JSF managed bean that handles the display and filtering of animals in the HomeTail application.
 * This bean is responsible for:
 * <ul>
 *   <li>Fetching and displaying a list of animals from the backend API</li>
 *   <li>Loading and managing animal categories</li>
 *   <li>Handling user filters and sorting preferences</li>
 *   <li>Managing pagination and search functionality</li>
 * </ul>
 * 
 * <p><b>Backend Integration:</b> Communicates with a REST API at {@code http://localhost:9090/api}.
 * The animals endpoint expects a {@code categoryId} parameter (Long) for filtering by category.</p>
 * 
 * <p><b>View Scoped:</b> Maintains state during user interaction with the view.</p>
 * 
 * @see AnimalDTO
 * @see jakarta.faces.view.ViewScoped
 */
@Named
@ViewScoped
public class ViewAnimalsBean implements Serializable {

    /** Serialization version UID for ensuring version compatibility. */
    @Serial private static final long serialVersionUID = 1L;

    // ---------- Configuration Properties ----------
    
    /** Base URL of the backend API. Default: http://localhost:9090 */
    private final static String API_BASE_URL = "http://localhost:9090";
    
    /** API endpoint path for fetching animals. Default: /api/animals */
    private String animalsPath = "/api/animals";
    
    /** API endpoint path for fetching categories. Returns DTOs with id and name. Default: /api/categories */
    private String categoriesPath = "/api/categories";
    
    /** Optional Bearer token for authenticated requests. */
    private String authToken;
    
    /** Base URL for resolving uploaded file paths. Default: http://localhost:9090 */
    private String uploadsBase = "http://localhost:9090";

    // ---------- Data Collections ----------
    
    /** List of animals currently displayed in the view. */
    private List<AnimalDTO> animals = new ArrayList<>();

    /**
     * Represents a category option for the category dropdown in the UI.
     * This DTO is used to map category data from the backend.
     */
    public static class CategoryOpt implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /** The unique identifier of the category. */
        public Long id;
        
        /** The display name of the category. */
        public String name;
        
        /** Default constructor required for JSF. */
        public CategoryOpt() {}
        
        /**
         * Creates a new category option with the specified ID and name.
         * 
         * @param id   The category ID
         * @param name The category name
         */
        public CategoryOpt(Long id, String name) { 
            this.id = id; 
            this.name = name; 
        }
        
        public Long getId() { return id; }
        public String getName() { return name; }
    }
    
    /** List of available categories for filtering animals. */
    private List<CategoryOpt> categories = new ArrayList<>();

    // ---------- Filter and Sort Options ----------
    
    /** Available gender options for filtering. */
    private final List<String> availableGenders = Arrays.asList("MALE", "FEMALE", "UNKNOWN");
    
    /** Available size options for filtering. */
    private final List<String> availableSizes = Arrays.asList("SMALL", "MEDIUM", "LARGE", "EXTRA_LARGE");
    
    /** Available age group options for filtering. */
    private final List<String> availableAgeGroups = Arrays.asList("BABY", "YOUNG", "ADULT", "SENIOR");

    // ---------- Filter Properties (Bound from XHTML) ----------
    
    /** Search query string for filtering animals by name or description. */
    private String q;
    
    /** Selected category ID for filtering animals. Maps to categoryId in the backend. */
    private Long categoryId;
    
    /** Selected gender for filtering animals. */
    private String gender;
    
    /** Selected size for filtering animals. */
    private String size;
    
    /** Flag indicating whether to show only available (not adopted) animals. */
    private boolean onlyAvailable;
    
    /** Selected age group for filtering animals. */
    private String ageGroup;

    // ---------- Sorting Properties ----------
    
    /** Field to sort by. Possible values: "name", "category", "age". Default: "name" */
    private String sortBy = "name";
    
    /** Sort order. Possible values: "asc", "desc". Default: "asc" */
    private String sortOrder = "asc";

    // ---------- Helper Objects ----------
    
    /** Jackson ObjectMapper for JSON serialization/deserialization. Marked as transient to avoid serialization. */
    private transient com.fasterxml.jackson.databind.ObjectMapper mapper;
    
    // ---------- Lifecycle Methods ----------
    
    /**
     * Initializes the bean after construction. This method is automatically called by the JSF framework
     * after the bean is instantiated. It performs the following actions:
     * 1. Initializes the JSON mapper
     * 2. Loads available categories from the backend
     * 3. Loads animals with the current filter settings
     */
    @PostConstruct
    public void init() {
        initializeMapper();
        loadCategoriesFromBackend();
        reloadFromBackend();
    }
    
    /**
     * Initializes the Jackson ObjectMapper with appropriate configuration.
     * The mapper is configured to:
     * - Support Java 8 date/time types via JavaTimeModule
     * - Write dates as ISO-8601 strings instead of timestamps
     * - Not adjust dates to the context time zone
     * - Ignore unknown properties during deserialization
     */
    private void initializeMapper() {
        if (mapper == null) {
            mapper = JsonMapper.builder()
                    .addModule(new JavaTimeModule())  // Enable LocalDate/LocalDateTime support
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
        }
    }

    // ---------- Action Methods ----------
    
    /**
     * Applies the current filter settings by reloading animals from the backend.
     * Any exceptions during the reload are caught and displayed as error messages.
     * 
     * @see #reloadFromBackend()
     */
    public void applyFilters() {
        try {
            reloadFromBackend();
        } catch (Exception ex) {
            String errorMsg = "Failed to load animals. " + 
                           (ex.getMessage() != null ? ex.getMessage() : "Please try again later.");
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", errorMsg));
            ex.printStackTrace();
        }
    }

    /**
     * Resets all filter values to their defaults and reloads the animal list.
     * This will:
     * - Clear search text
     * - Reset category filter
     * - Clear gender, size, and age group filters
     * - Show all animals (including adopted ones)
     * - Reset sorting to default (name ascending)
     * 
     * @see #reloadFromBackend()
     */
    public void clearFilters() {
        // Reset all filter values
        q = null;
        categoryId = null;
        gender = null;
        size = null;
        onlyAvailable = false;
        ageGroup = null;
        
        // Reset sorting
        sortBy = "name";
        sortOrder = "asc";
        
        // Reload with cleared filters
        reloadFromBackend();
    }

    /**
     * Reloads the list of animals from the backend API using the current filter settings.
     * This method:
     * 1. Builds the appropriate API URL with current filters
     * 2. Makes an HTTP GET request to fetch the data
     * 3. Handles different response formats (array or page object)
     * 4. Ensures age descriptions are set for all animals
     * 5. Applies client-side sorting if needed
     * 
     * <p>If an error occurs during loading, an error message is logged and an empty list is displayed.</p>
     * 
     * @see #buildAnimalsUrl()
     * @see #ensureAgeDescription(AnimalDTO)
     * @see #compareBySort(AnimalDTO, AnimalDTO)
     */
    public void reloadFromBackend() {
        try {
            // Ensure JSON mapper is initialized
            initializeMapper();
            
            // Build URL with current filters and fetch data
            String url = buildAnimalsUrl();
            String body = httpGet(url, authToken);

            // Handle different response formats (array or page object with "content")
            JsonNode root = mapper.readTree(body);
            List<AnimalDTO> fetched;
            
            if (root.isArray()) {
                // Response is a direct array of animals
                fetched = mapper.convertValue(root, new TypeReference<List<AnimalDTO>>() {});
            } else if (root.isObject() && root.has("content") && root.get("content").isArray()) {
                // Response is a page object with "content" array (Spring Data format)
                fetched = mapper.convertValue(root.get("content"), new TypeReference<List<AnimalDTO>>() {});
            } else {
                // Fallback: try to parse as list directly
                fetched = mapper.readValue(body, new TypeReference<List<AnimalDTO>>() {});
            }
            
            // Ensure we have a non-null list
            if (fetched == null) {
                fetched = new ArrayList<>();
            }

            // Ensure age descriptions are set for all animals
            fetched.forEach(this::ensureAgeDescription);

            // Apply client-side sorting and update the animals list
            animals = fetched.stream()
                .sorted(this::compareBySort)
                .collect(Collectors.toList());

        } catch (Exception e) {
            // Log error and display empty list
            String errorMsg = "Failed to load animals: " + e.getMessage();
            System.err.println("[ViewAnimalsBean] " + errorMsg);
            e.printStackTrace();
            
            // Show error message to user
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", 
                               "Failed to load animals. Please try again later."));
                
            animals = new ArrayList<>();
        }
    }

    /**
     * Loads the list of animal categories from the backend API.
     * This method:
     * 1. Constructs the categories endpoint URL
     * 2. Makes an HTTP GET request to fetch the categories
     * 3. Parses the JSON response into a list of CategoryOpt objects
     * 4. Sorts categories alphabetically by name (case-insensitive)
     * 
     * <p>If an error occurs, an empty list is used and the error is logged.</p>
     * 
     * @see CategoryOpt
     * @see #httpGet(String, String)
     */
    private void loadCategoriesFromBackend() {
        try {
            // Ensure JSON mapper is initialized
            initializeMapper();
            
            // Build the categories endpoint URL
            String url = API_BASE_URL + (categoriesPath.startsWith("/") ? categoriesPath : ("/" + categoriesPath));
            
            // Fetch categories from the backend
            String body = httpGet(url, authToken);

            // Parse the JSON response into a list of CategoryOpt objects
            // Expected format: [{ "id": 1, "name": "Dog" }, ...]
            List<CategoryOpt> list = mapper.readValue(body, new TypeReference<List<CategoryOpt>>() {});
            
            // Ensure we have a non-null list
            if (list == null) {
                list = new ArrayList<>();
            }
            
            // Sort categories alphabetically by name (case-insensitive)
            list.sort(Comparator.comparing(CategoryOpt::getName, String.CASE_INSENSITIVE_ORDER));
            
            // Update the categories list
            categories = list;
            
        } catch (Exception e) {
            // Log error and use empty list
            String errorMsg = "Failed to load categories: " + e.getMessage();
            System.err.println("[ViewAnimalsBean] " + errorMsg);
            e.printStackTrace();
            
            // Show error message to user
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning", 
                               "Could not load animal categories. Some filtering options may be unavailable."));
                
            categories = new ArrayList<>();
        }
    }

    // ---------- URL Building ----------
    
    /**
     * Constructs the URL for fetching animals based on current filter settings.
     * The URL is built by combining:
     * - The base API URL
     * - The animals endpoint path
     * - Query parameters for all active filters
     * 
     * <p>Supported query parameters:
     * <ul>
     *   <li><code>q</code> - Search query string</li>
     *   <li><code>categoryId</code> - Filter by category ID</li>
     *   <li><code>gender</code> - Filter by gender (MALE/FEMALE)</li>
     *   <li><code>animalSize</code> - Filter by size (SMALL/MEDIUM/LARGE/EXTRA_LARGE)</li>
     *   <li><code>ageGroup</code> - Filter by age group (BABY/YOUNG/ADULT/SENIOR)</li>
     *   <li><code>adopted</code> - Set to "false" to show only available animals</li>
     * </ul>
     * 
     * @return The fully constructed URL with query parameters
     * @see #encode(String)
     */
    private String buildAnimalsUrl() {
        StringBuilder sb = new StringBuilder();
        
        // Add base URL and ensure proper path separation
        sb.append(API_BASE_URL);
        if (!animalsPath.startsWith("/")) {
            sb.append('/');
        }
        sb.append(animalsPath);

        // Build query parameters map
        Map<String, String> params = new LinkedHashMap<>();
        if (notBlank(q))          params.put("q", q);
        if (categoryId != null)   params.put("categoryId", String.valueOf(categoryId));
        if (notBlank(gender))     params.put("gender", gender);
        if (notBlank(size))       params.put("animalSize", size);
        if (notBlank(ageGroup))   params.put("ageGroup", ageGroup);
        if (onlyAvailable)        params.put("adopted", "false");

        // Append query parameters if any exist
        if (!params.isEmpty()) {
            sb.append('?');
            sb.append(
                params.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"))
            );
        }
        
        return sb.toString();
    }

    /**
     * URL-encodes a string using UTF-8 encoding.
     * This is used to properly encode query parameter values.
     *
     * @param s The string to encode
     * @return The URL-encoded string
     * @throws UnsupportedEncodingException If UTF-8 encoding is not supported (should never happen)
     */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * Performs an HTTP GET request to the specified URL with optional Bearer token authentication.
     * 
     * @param urlStr The URL to send the GET request to
     * @param bearerToken Optional Bearer token for authentication (can be null or empty)
     * @return The response body as a string
     * @throws IOException if an I/O error occurs or if the server returns an error status code
     * @throws IllegalArgumentException if the URL is malformed or null
     */
    private String httpGet(String urlStr, String bearerToken) throws IOException {
        if (urlStr == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        
        HttpURLConnection conn = null;
        try {
            // Create and configure the connection
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            
            // Add Bearer token if provided
            if (bearerToken != null && !bearerToken.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
            }

            // Check for error response
            int status = conn.getResponseCode();
            if (status >= 400) {
                String errorMsg = readErrorStream(conn);
                throw new IOException("HTTP error " + status + " - " + errorMsg);
            }

            // Read and return the response body
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return br.lines().collect(Collectors.joining());
            }
        } finally {
            // Ensure connection is always closed
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Reads the error stream from an HTTP connection and returns its content as a string.
     * This is used to extract error details when an HTTP error occurs.
     * 
     * @param conn The HTTP connection to read the error stream from
     * @return The error message as a string, or a default message if the stream is empty
     */
    private String readErrorStream(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null) {
                return "No error details available";
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return "Error reading error stream: " + e.getMessage();
        }
    }

    // ---------- Rendering helpers ----------
    /** Turn '/uploads/...' into absolute URL for <h:graphicImage>. */
    public String resolveImage(String img) {
        if (img == null) return "";
        return img.startsWith("/uploads/") ? uploadsBase + img : img;
    }

    private void ensureAgeDescription(AnimalDTO a) {
        if (a.getAgeDescription() != null && !a.getAgeDescription().isBlank()) return;
        LocalDate birthday = a.getBirthday();
        if (birthday == null) {
            a.setAgeDescription("Unknown");
            return;
        }
        Period p = Period.between(birthday, LocalDate.now());
        if (p.getYears() > 0) {
            a.setAgeDescription(p.getYears() + (p.getYears() == 1 ? " year" : " years"));
        } else if (p.getMonths() > 0) {
            a.setAgeDescription(p.getMonths() + (p.getMonths() == 1 ? " month" : " months"));
        } else {
            int days = Math.max(p.getDays(), 0);
            a.setAgeDescription(days + (days == 1 ? " day" : " days"));
        }
    }

    // ---------- Sorting and Comparison ----------
    
    /**
     * Compares two AnimalDTO objects based on the current sort settings.
     * The comparison is case-insensitive for string fields and handles null values.
     * 
     * @param a The first animal DTO to compare
     * @param b The second animal DTO to compare
     * @return A negative integer, zero, or a positive integer as the first argument 
     *         is less than, equal to, or greater than the second.
     */
    private int compareBySort(AnimalDTO a, AnimalDTO b) {
        int cmp;
        switch (sortBy == null ? "name" : sortBy) {
            case "category":
                // Compare by category name
                cmp = compareNullSafeStr(a.getCategory(), b.getCategory());
                break;
            case "age":
                // Compare by age in days (smaller = younger)
                cmp = Integer.compare(ageInDays(a), ageInDays(b));
                break;
            case "name":
            default:
                // Default: compare by animal name
                cmp = compareNullSafeStr(a.getName(), b.getName());
                break;
        }
        // Apply sort order (ascending or descending)
        if ("desc".equalsIgnoreCase(sortOrder)) {
            cmp = -cmp;
        }
        return cmp;
    }

    /**
     * Calculates the approximate age of an animal in days.
     * This is a rough calculation that assumes 365 days per year and 30 days per month.
     * 
     * @param a The animal DTO to calculate age for
     * @return The age of the animal in days, or Integer.MAX_VALUE if birthdate is unknown
     */
    private int ageInDays(AnimalDTO a) {
        LocalDate bd = a.getBirthday();
        if (bd == null) {
            return Integer.MAX_VALUE; // Unknown birthdate sorts last in ascending order
        }
        
        // Calculate period between birthdate and now
        Period p = Period.between(bd, LocalDate.now());
        
        // Convert to approximate days (365 days/year, 30 days/month)
        return Math.max(0, p.getYears() * 365 + p.getMonths() * 30 + p.getDays());
    }

    /**
     * Compares two strings in a null-safe, case-insensitive manner.
     * Null values are considered greater than non-null values.
     * 
     * @param x The first string to compare
     * @param y The second string to compare
     * @return A negative integer, zero, or a positive integer as the first argument 
     *         is less than, equal to, or greater than the second.
     */
    private int compareNullSafeStr(String x, String y) {
        if (x == null && y == null) return 0;
        if (x == null) return 1;      // nulls last
        if (y == null) return -1;     // nulls last
        return x.compareToIgnoreCase(y);
    }

    /**
     * Checks if a string is not blank (not null, not empty, and contains at least one non-whitespace character).
     * 
     * @param s The string to check
     * @return True if the string is not blank, false otherwise
     */
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ---------- Getters / Setters (for XHTML binding) ----------
    /**
     * Gets the list of animals to display.
     * 
     * @return The list of animals
     */
    public List<AnimalDTO> getAnimals() { return animals; }

    /**
     * Gets the list of available animal categories.
     * 
     * @return The list of categories
     */
    public List<CategoryOpt> getCategories() { return categories; }

    /**
     * Gets the list of available gender options.
     * 
     * @return The list of gender options
     */
    public List<String> getAvailableGenders() { return availableGenders; }

    /**
     * Gets the list of available size options.
     * 
     * @return The list of size options
     */
    public List<String> getAvailableSizes() { return availableSizes; }

    /**
     * Gets the list of available age group options.
     * 
     * @return The list of age group options
     */
    public List<String> getAvailableAgeGroups() { return availableAgeGroups; }

    /**
     * Gets the current search query string.
     * 
     * @return The search query string
     */
    public String getQ() { return q; }

    /**
     * Sets the search query string.
     * 
     * @param q The search query string to set
     */
    public void setQ(String q) { this.q = q; }

    /**
     * Gets the currently selected category ID.
     * 
     * @return The category ID
     */
    public Long getCategoryId() { return categoryId; }

    /**
     * Sets the category ID to filter by.
     * 
     * @param categoryId The category ID to set
     */
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    /**
     * Gets the currently selected gender filter.
     * 
     * @return The gender filter
     */
    public String getGender() { return gender; }

    /**
     * Sets the gender to filter by.
     * 
     * @param gender The gender to set
     */
    public void setGender(String gender) { this.gender = gender; }

    /**
     * Gets the currently selected size filter.
     * 
     * @return The size filter
     */
    public String getSize() { return size; }

    /**
     * Sets the size to filter by.
     * 
     * @param size The size to set
     */
    public void setSize(String size) { this.size = size; }

    /**
     * Checks whether to show only available (not adopted) animals.
     * 
     * @return True if only available animals should be shown, false otherwise
     */
    public boolean isOnlyAvailable() { return onlyAvailable; }

    /**
     * Sets whether to show only available animals.
     * 
     * @param onlyAvailable Whether to show only available animals
     */
    public void setOnlyAvailable(boolean onlyAvailable) { this.onlyAvailable = onlyAvailable; }

    /**
     * Gets the currently selected age group filter.
     * 
     * @return The age group filter
     */
    public String getAgeGroup() { return ageGroup; }

    /**
     * Sets the age group to filter by.
     * 
     * @param ageGroup The age group to set
     */
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }

    /**
     * Gets the current field to sort by (name, category, or age).
     * 
     * @return The field to sort by
     */
    public String getSortBy() { return sortBy; }

    /**
     * Sets the field to sort by (name, category, or age).
     * 
     * @param sortBy The field to sort by
     */
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    /**
     * Gets the current sort order (asc or desc).
     * 
     * @return The sort order
     */
    public String getSortOrder() { return sortOrder; }

    /**
     * Sets the sort order (asc or desc).
     * 
     * @param sortOrder The sort order to set
     */
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }

    public String getAnimalsPath() { return animalsPath; }
    public void setAnimalsPath(String animalsPath) { this.animalsPath = animalsPath; }

    public String getCategoriesPath() { return categoriesPath; }
    public void setCategoriesPath(String categoriesPath) { this.categoriesPath = categoriesPath; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public String getUploadsBase() { return uploadsBase; }
    public void setUploadsBase(String uploadsBase) { this.uploadsBase = uploadsBase; }

    // Helper method to get category name for display
    public String getCategory() {
        if (categoryId == null) return null;
        return categories.stream()
                .filter(c -> Objects.equals(c.getId(), categoryId))
                .map(CategoryOpt::getName)
                .findFirst()
                .orElse(null);
    }
}
