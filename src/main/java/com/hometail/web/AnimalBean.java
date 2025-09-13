package com.hometail.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hometail.web.dto.AnimalDTO;
import com.hometail.web.dto.BreedDTO;
import com.hometail.web.dto.CategoryDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.annotation.ManagedProperty;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ComponentSystemEvent;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Managed bean for handling animal-related operations in the HomeTail application.
 * This view-scoped bean manages the UI state and business logic for animal management,
 * including CRUD operations, image uploads, and form handling.
 * 
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Managing form data for creating/editing animals</li>
 *   <li>Handling file uploads for animal images</li>
 *   <li>Loading categories and breeds for dropdowns</li>
 *   <li>Communicating with the backend API</li>
 *   <li>Providing pagination for animal listings</li>
 * </ul>
 */
@Named("animalBean")
@ViewScoped
@Getter @Setter
public class AnimalBean implements Serializable {

    // ---- Routing / identity ----
    /**
     * The ID of the animal being viewed/edited, extracted from URL parameter.
     * Used to determine if we're creating a new animal or editing an existing one.
     */
    @ManagedProperty(value = "#{param.id}")
    private Long animalId;

    // ---- Form fields (bound to XHTML) ----
    /** Animal's name (required field) */
    private String name;
    
    /** 
     * ID of the selected category.
     * Must be Long to match JSF selectItems component's itemValue.
     */
    private Long categoryId;
    
    /** 
     * ID of the selected breed.
     * Must be Long to match JSF selectItems component's itemValue.
     */
    private Long breedId;
    
    /** 
     * Animal's gender. Expected values: "MALE" or "FEMALE".
     * Mapped from the genderOptions map for display purposes.
     */
    private String gender;
    
    /** Animal's date of birth in yyyy-MM-dd format */
    private LocalDate birthday;
    
    /** 
     * Animal's size category.
     * Expected values: "SMALL", "MEDIUM", "LARGE", or "EXTRA_LARGE".
     * Mapped from the sizeOptions map for display purposes.
     */
    private String size;
    
    /** Brief description shown in animal listings */
    private String shortDescription;
    
    /** Detailed description shown on the animal's detail page */
    private String longDescription;
    
    /** Age description in human-readable format (e.g., "2 years old") */
    private String ageDescription;

    // ---- Image handling ----
    /** 
     * URL or path to the animal's primary image.
     * This is stored in the database and used to display the image.
     */
    private String image;
    
    /** 
     * Legacy field for file uploads.
     * @deprecated Use uploadedFile instead.
     */
    private Part imageFile;
    
    /** Currently selected file for upload in the form */
    private Part uploadedFile;

    // ---- Data collections ----
    /** List of all available animal categories for the category dropdown */
    private List<CategoryDTO> categories = new ArrayList<>();
    
    /** List of breeds for the currently selected category */
    private List<BreedDTO> breeds = new ArrayList<>();
    
    /** 
     * List of animals after applying any filters and pagination.
     * Used to display the animal listing table.
     */
    private List<AnimalDTO> filteredAnimals = new ArrayList<>();

    // ---- Pagination ----
    /** Current page number (0-based) for the animal listing */
    private int currentPage = 0;
    
    /** Number of animals to display per page */
    private final int pageSize = 5;

    // ---- UI Option Maps ----
    /**
     * Maps user-friendly gender labels to their corresponding API values.
     * Used to populate gender selection dropdowns in the UI.
     * Key: Display text shown to users
     * Value: Internal API value
     */
    private static final Map<String, String> genderOptions = new LinkedHashMap<>();
    
    /**
     * Maps user-friendly size labels to their corresponding API values.
     * Used to populate size selection dropdowns in the UI.
     * Key: Display text shown to users
     * Value: Internal API value (in uppercase with underscores)
     */
    private static final Map<String, String> sizeOptions = new LinkedHashMap<>();
    
    // Initialize option maps with user-friendly labels and their corresponding API values
    static {
        // Gender options with display text and corresponding API values
        genderOptions.put("Male",   "MALE");
        genderOptions.put("Female", "FEMALE");
        genderOptions.put("Unknown", "UNKNOWN");

        // Size options with display text and corresponding API values
        sizeOptions.put("Small",       "SMALL");
        sizeOptions.put("Medium",      "MEDIUM");
        sizeOptions.put("Large",       "LARGE");
        sizeOptions.put("Extra Large", "EXTRA_LARGE");
    }

    // ---- Dependencies ----
    /** 
     * Injected LoginBean for authentication and user context.
     * Provides access to the current user's information and authentication token.
     */
    @Inject
    private LoginBean loginBean;

    // ---- Constants ----
    /** 
     * Base URL for all API endpoints.
     * Points to the backend service that handles animal-related operations.
     */
    private static final String API_BASE = "http://localhost:9090/api";

    // ---------------------------------------------------------------------
    // Lifecycle Methods
    // ---------------------------------------------------------------------
    
    /**
     * Initializes the bean after construction.
     * This method is automatically called by the container after dependency injection.
     * It performs the following tasks:
     * 1. Loads categories (always needed for the form)
     * 2. Checks for animal ID in request parameters
     * 3. If in edit mode, loads the animal's data and related breeds
     */
    @PostConstruct
    public void init() {
        // Always load categories as they're needed for the form dropdown
        loadCategories();

        // If animalId wasn't set via @ManagedProperty, try to get it from request parameters
        // This handles direct URL access (e.g., /edit-animal.xhtml?id=123)
        if (animalId == null) {
            String idParam = FacesContext.getCurrentInstance()
                    .getExternalContext().getRequestParameterMap().get("id");
            if (idParam != null && !idParam.isBlank()) {
                try { 
                    animalId = Long.valueOf(idParam); 
                } catch (NumberFormatException ignored) {
                    // Silently ignore invalid IDs - will be handled as a new animal
                }
            }
        }

        // If we have an animal ID and this is the initial page load (not a postback),
        // load the animal's data for editing
        if (animalId != null && !FacesContext.getCurrentInstance().isPostback()) {
            loadAnimalForEdit(animalId);
        }
    }

    /**
     * Pre-render view event handler.
     * Ensures animal data is loaded before rendering the view.
     * This method is called by JSF before rendering the view and is safe to call multiple times.
     */
    public void onPreRender() {
        // Only load if this is the initial page load (not a postback) and we have an animal ID
        if (!FacesContext.getCurrentInstance().isPostback() && animalId != null) {
            loadAnimalForEdit(animalId);
        }
    }

    /**
     * Backward compatibility method for XHTML listener.
     * Delegates to onPreRender().
     * 
     * @param event The component system event (unused)
     */
    public void loadAnimalForEditListener(ComponentSystemEvent event) {
        onPreRender();
    }

    // ---------------------------------------------------------------------
    // UI Event Handlers
    // ---------------------------------------------------------------------
    
    /**
     * Handles category selection change event.
     * This method is called via AJAX when the user selects a category.
     * It performs the following actions:
     * 1. Resets the selected breed (since breeds are category-specific)
     * 2. Loads breeds for the selected category
     */
    public void onCategoryChange() {
        // Reset selected breed when category changes
        breedId = null;
        
        // Load breeds for the selected category if a category is selected
        if (categoryId != null) {
            loadBreeds(categoryId);
        } else {
            // Clear breeds list if no category is selected
            breeds = Collections.emptyList();
        }
    }

    // ---------------------------------------------------------------------
    // Data Loading Methods
    // ---------------------------------------------------------------------
    
    /**
     * Loads all active categories from the API.
     * Populates the categories list used in the category dropdown.
     * Any errors during loading are caught and displayed to the user.
     */
    private void loadCategories() {
        try {
            // Fetch active categories from the API
            String json = request("GET", API_BASE + "/categories?active=true", null);
            
            // Parse the JSON response into a list of CategoryDTO objects
            categories = JsonUtils.getObjectMapper()
                    .readValue(json, new TypeReference<List<CategoryDTO>>() {});
        } catch (Exception e) {
            // On error, initialize with empty list and show error message
            categories = new ArrayList<>();
            addError("Failed to load categories: " + e.getMessage());
        }
    }

    /**
     * Loads breeds for a specific category from the API.
     * 
     * @param catId The ID of the category to load breeds for
     */
    private void loadBreeds(Long catId) {
        try {
            // Fetch breeds for the specified category from the API
            String json = request("GET", API_BASE + "/breeds?categoryId=" + catId, null);
            
            // Parse the JSON response into a list of BreedDTO objects
            breeds = JsonUtils.getObjectMapper()
                    .readValue(json, new TypeReference<List<BreedDTO>>() {});
        } catch (Exception e) {
            // On error, initialize with empty list and show error message
            breeds = new ArrayList<>();
            addError("Failed to load breeds: " + e.getMessage());
        }
    }

    /**
     * Loads an animal's data for editing.
     * This method fetches an animal's details from the API and populates the form fields.
     * It handles both direct ID-based lookups and name-based fallbacks for category/breed.
     * 
     * @param id The ID of the animal to load
     */
    private void loadAnimalForEdit(Long id) {
        try {
            // Fetch the animal's data from the API
            String json = request("GET", API_BASE + "/animals/" + id, null);
            AnimalDTO a = JsonUtils.getObjectMapper().readValue(json, AnimalDTO.class);

            // Map basic fields from DTO to form fields
            this.animalId         = a.getId();
            this.name             = a.getName();
            this.gender           = normalizeEnum(a.getGender());   // Ensure proper case ("MALE"/"FEMALE")
            this.size             = normalizeEnum(a.getSize());     // Ensure proper case ("SMALL"/"MEDIUM"/"LARGE")
            this.birthday         = a.getBirthday();
            this.shortDescription = a.getShortDescription();
            this.longDescription  = a.getLongDescription();
            this.ageDescription   = a.getAgeDescription();
            this.image            = a.getImage();

            // ---- Handle Category ----
            // First try to use the category ID from the DTO
            Long resolvedCategoryId = a.getCategoryId();
            
            // If no category ID, but we have a category name, try to find matching category by name
            if (resolvedCategoryId == null && a.getCategoryName() != null) {
                resolvedCategoryId = categories.stream()
                        .filter(c -> a.getCategoryName().equalsIgnoreCase(c.getName()))
                        .map(CategoryDTO::getId)
                        .findFirst().orElse(null);
            }
            this.categoryId = resolvedCategoryId;

            // Load breeds for the selected category to populate the breed dropdown
            if (this.categoryId != null) {
                loadBreeds(this.categoryId);
            } else {
                breeds = Collections.emptyList();
            }

            // ---- Handle Breed ----
            // First try to use the breed ID from the DTO
            Long resolvedBreedId = a.getBreedId();
            
            // If no breed ID, but we have a breed name, try to find matching breed by name
            if (resolvedBreedId == null && a.getBreedName() != null && !breeds.isEmpty()) {
                resolvedBreedId = breeds.stream()
                        .filter(b -> a.getBreedName().equalsIgnoreCase(b.getName()))
                        .map(BreedDTO::getId)
                        .findFirst().orElse(null);
            }
            this.breedId = resolvedBreedId;

        } catch (Exception e) {
            addError("Error loading animal: " + e.getMessage());
        }
    }

    /**
     * Normalizes enum strings to a consistent format.
     * Converts input to uppercase and replaces spaces with underscores.
     * Examples: "Male" -> "MALE", "medium" -> "MEDIUM", "extra large" -> "EXTRA_LARGE"
     * 
     * @param v The input string to normalize
     * @return The normalized string, or null if input was null
     */
    private String normalizeEnum(String v) {
        return (v == null) ? null : v.trim().replace(' ', '_').toUpperCase();
    }


    // ---------------------------------------------------------------------
    // Action Methods
    // ---------------------------------------------------------------------
    
    /**
     * Handles form submission for creating or updating an animal.
     * This method is called when the user submits the animal form.
     * It performs the following actions:
     * 1. Builds a JSON payload from the form fields
     * 2. Handles file upload if an image was selected
     * 3. Submits the data to the appropriate API endpoint
     * 4. Shows success/error messages
     * 5. Redirects to the animal list on success
     * 
     * @return Navigation outcome string (URL to redirect to), or null to stay on the same page
     */
    public String submitAnimal() {
        try {
            // Create a JSON object that matches the backend's AnimalDTO structure
            ObjectMapper mapper = JsonUtils.getObjectMapper();
            ObjectNode node = mapper.createObjectNode();
            
            // Include ID only for updates (not for new animals)
            if (animalId != null) {
                node.put("id", animalId);
            }
            
            // Add all form fields to the JSON payload
            node.put("name", name);
            node.put("categoryId", categoryId);
            node.put("breedId", breedId);
            node.put("gender", gender);      // enum code (e.g., "MALE")
            node.put("size", size);          // enum code (e.g., "MEDIUM")
            if (birthday != null) {
                node.put("birthday", birthday.toString()); // Format: yyyy-MM-dd
            }
            node.put("shortDescription", shortDescription);
            node.put("longDescription", longDescription);
            node.put("ownerId", loginBean.getUserId());

            // Convert the JSON node to a string
            String json = mapper.writeValueAsString(node);

            // Determine the appropriate API endpoint and HTTP method
            boolean isNewAnimal = (animalId == null);
            String url = isNewAnimal 
                    ? API_BASE + "/animals"
                    : API_BASE + "/animals/" + animalId;
            String httpMethod = isNewAnimal ? "POST" : "PUT";

            // Submit the form data, including any uploaded file
            String body = postMultipart(
                    url, 
                    httpMethod,
                    "animal",         // Name of the JSON part in the multipart request
                    json,             // The JSON payload
                    "image",          // Name of the file part in the multipart request
                    uploadedFile,      // The uploaded file (can be null)
                    loginBean.getToken() // Authentication token
            );

            // Set up success message to be shown after redirect
            FacesContext ctx = FacesContext.getCurrentInstance();
            ctx.getExternalContext().getFlash().setKeepMessages(true);
            ctx.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_INFO,
                    (isNewAnimal ? "Animal added" : "Animal updated") + " successfully!",
                    null
            ));

            // Redirect to the animal list page after successful submission
            return "myAnimals.xhtml?faces-redirect=true";

        } catch (Exception e) {
            // Show error message and stay on the same page
            addError("Error saving animal: " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------
    
    /**
     * Adds an error message to the FacesContext to be displayed to the user.
     * The message will be displayed in any h:messages or p:messages component.
     * 
     * @param msg The error message to display
     */
    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    /**
     * Sends an HTTP request and returns the response body as a string.
     * Handles both GET and POST requests with JSON payloads.
     * Automatically includes authentication token if available.
     * 
     * @param method The HTTP method ("GET", "POST", "PUT", "DELETE", etc.)
     * @param urlStr The full URL to send the request to
     * @param json The JSON payload to send (can be null for GET requests)
     * @return The response body as a string
     * @throws IOException if an I/O error occurs or if the server returns an error status code
     */
    private String request(String method, String urlStr, String json) throws IOException {
        // Create and configure the HTTP connection
        HttpURLConnection c = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);  // 12 seconds connection timeout
        c.setReadTimeout(30000);     // 30 seconds read timeout
        c.setRequestProperty("Accept", "application/json");
        
        // Add authorization header if token is available
        String token = (loginBean != null) ? loginBean.getToken() : null;
        if (token != null && !token.isBlank()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }

        // For requests with a JSON body (POST, PUT, etc.)
        if (json != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        // Read the response
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = (is != null) ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        
        // Throw exception for error status codes
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        
        return body;
    }

    /**
     * Sends a multipart/form-data request with both JSON and file parts.
     * This is used for submitting forms that include file uploads along with JSON data.
     * 
     * @param urlStr The URL to send the request to
     * @param method The HTTP method (usually "POST" or "PUT")
     * @param jsonPartName The name of the form field for the JSON data
     * @param json The JSON data to send
     * @param filePartName The name of the form field for the file
     * @param file The file part to upload (can be null)
     * @param bearerToken The authentication token (can be null)
     * @return The response body as a string
     * @throws IOException if an I/O error occurs or if the server returns an error status code
     */
    private String postMultipart(String urlStr, String method,
                               String jsonPartName, String json,
                               String filePartName, Part file,
                               String bearerToken) throws IOException {
        // Generate a unique boundary string for the multipart request
        String boundary = "----HomeTailBoundary" + System.nanoTime();
        
        // Set up the HTTP connection
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setDoOutput(true);
        conn.setConnectTimeout(12000);  // 12 seconds connection timeout
        conn.setReadTimeout(30000);     // 30 seconds read timeout
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        // Add authorization header if token is provided
        if (bearerToken != null && !bearerToken.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }

        // CRLF constant for line endings in the multipart request
        final String CRLF = "\r\n";
        
        try (OutputStream out = conn.getOutputStream()) {
            // 1. Add the JSON part
            out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + jsonPartName + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/json; charset=UTF-8" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.write(CRLF.getBytes(StandardCharsets.UTF_8));

            // 2. Add the file part (if provided and has content)
            if (file != null && file.getSize() > 0) {
                // Get filename and content type, with fallbacks
                String filename = Optional.ofNullable(file.getSubmittedFileName()).orElse("upload.bin");
                String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");

                // Write file part headers
                out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Disposition: form-data; name=\"" + filePartName + "\"; filename=\"" + filename + "\"" + CRLF)
                        .getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                
                // Write file content
                try (InputStream in = file.getInputStream()) { 
                    in.transferTo(out); 
                }
                out.write(CRLF.getBytes(StandardCharsets.UTF_8));
            }

            // 3. Write the final boundary to end the multipart request
            out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        }

        // Read the response
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = (is != null) ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        
        // Throw exception for error status codes
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        
        return body;
    }

    // ---------------------------------------------------------------------
    // Getters for UI Components
    // ---------------------------------------------------------------------
    
    /**
     * Gets the gender options map for the UI dropdown.
     * The map contains user-friendly display names as keys and corresponding API values.
     * 
     * @return Map of gender options
     */
    public Map<String, String> getGenderOptions() { 
        return genderOptions; 
    }
    
    /**
     * Gets the size options map for the UI dropdown.
     * The map contains user-friendly display names as keys and corresponding API values.
     * 
     * @return Map of size options
     */
    public Map<String, String> getSizeOptions() { 
        return sizeOptions; 
    }
    
}
