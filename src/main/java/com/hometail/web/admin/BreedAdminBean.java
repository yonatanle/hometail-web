package com.hometail.web.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hometail.web.JsonUtils;
import com.hometail.web.LoginBean;
import com.hometail.web.dto.BreedDTO;
import com.hometail.web.dto.CategoryDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Backing bean for managing breeds in the admin interface.
 * Handles CRUD operations for breeds including creation, editing, and deletion.
 * Provides filtering capabilities by category.
 */
@Named("breedAdminBean")
@ViewScoped
public class BreedAdminBean implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    /** Base URL for API endpoints */
    private static final String API_BASE = "http://localhost:9090/api";
    
    /** Base URL for admin API endpoints */
    private static final String ADMIN_API_BASE = API_BASE + "/admin";

    /** Injected login bean for authentication */
    @Inject 
    private LoginBean loginBean;

    // --- Data for UI ---
    
    /** List of all available categories for filtering */
    private List<CategoryDTO> categories = new ArrayList<>();
    
    /** Currently selected category ID for filtering breeds */
    private Long filterCategoryId;
    
    /** List of breeds to display in the UI */
    private List<BreedDTO> breeds = new ArrayList<>();

    /** Current breed being edited/created in the form */
    private BreedDTO form = new BreedDTO();
    
    /** Flag indicating if we're in edit mode (true) or create mode (false) */
    private boolean editing = false;

    /** Breed marked for deletion */
    private BreedDTO toDelete;

    /**
     * Initializes the bean by loading categories and breeds.
     * Called automatically after dependency injection is done.
     */
    @PostConstruct
    public void init() {
        reloadCategories();
        reloadBreeds();
    }

    // ======== Actions used by the page ========

    /**
     * Reloads the list of breeds from the server.
     * Displays an info message when complete.
     */
    public void reload() {
        reloadBreeds();
        info("Breeds reloaded");
    }

    /**
     * Applies the category filter and reloads breeds.
     * Typically called via AJAX when the category filter changes.
     */
    public void applyCategoryFilter() {
        reloadBreeds();
    }

    /**
     * Prepares the form for creating a new breed.
     * Sets up a new empty breed with default values.
     */
    public void startCreate() {
        editing = false;
        form = new BreedDTO();
        form.setActive(Boolean.TRUE);
        if (filterCategoryId != null) form.setCategoryId(filterCategoryId);
    }

    /**
     * Prepares the form for editing an existing breed.
     * 
     * @param b The breed to edit
     */
    public void startEdit(BreedDTO b) {
        editing = true;
        form = new BreedDTO();
        form.setId(b.getId());
        form.setName(b.getName());
        form.setCategoryId(b.getCategoryId());
        form.setCategoryName(b.getCategoryName());
        form.setActive(b.getActive() != null ? b.getActive() : Boolean.TRUE);
        form.setSortOrder(b.getSortOrder());
    }

    /**
     * Marks a breed for deletion and shows a confirmation dialog.
     * 
     * @param b The breed to be deleted
     */
    public void confirmDelete(BreedDTO b) {
        toDelete = b;
    }

    /**
     * Deletes the currently selected breed after confirmation.
     * Reloads the breed list after successful deletion.
     */
    public void deleteSelected() {
        if (toDelete == null || toDelete.getId() == null) return;
        try {
            request("DELETE", ADMIN_API_BASE + "/breeds/" + toDelete.getId(), null);
            info("Breed deleted");
            reloadBreeds();
        } catch (IOException e) {
            error("Delete failed: " + e.getMessage());
        } finally {
            toDelete = null;
        }
    }

    /**
     * Saves the current breed (create or update).
     * Performs basic validation before sending to the server.
     */
    public void save() {
        try {
            // Basic validation (backend does more thorough validation)
            if (form.getName() == null || form.getName().isBlank()) {
                error("Name is required"); 
                return;
            }
            if (form.getCategoryId() == null) {
                error("Category is required"); 
                return;
            }

            String json = JsonUtils.getObjectMapper().writeValueAsString(form);
            if (editing && form.getId() != null) {
                // Update existing breed
                request("PUT", ADMIN_API_BASE + "/breeds/" + form.getId(), json);
                info("Breed updated");
            } else {
                // Create new breed
                request("POST", ADMIN_API_BASE + "/breeds", json);
                info("Breed created");
            }
            reloadBreeds();
        } catch (Exception e) {
            error("Save failed: " + e.getMessage());
        }
    }

    /**
     * Gets the category name for a given category ID.
     * 
     * @param id The category ID to look up
     * @return The category name, or an empty string if not found
     */
    public String categoryName(Long id) {
        if (id == null) return "";
        for (CategoryDTO c : categories) {
            if (Objects.equals(c.getId(), id)) return c.getName();
        }
        return "";
    }

    // ======== Loaders ========

    /**
     * Reloads the list of categories from the server.
     * Categories are sorted alphabetically by name.
     */
    private void reloadCategories() {
        try {
            String json = request("GET", ADMIN_API_BASE + "/categories?active=true", null);
            List<CategoryDTO> list = JsonUtils.getObjectMapper()
                    .readValue(json, new TypeReference<List<CategoryDTO>>() {});
            list.sort(Comparator.comparing(CategoryDTO::getName, String.CASE_INSENSITIVE_ORDER));
            categories = list;
        } catch (Exception e) {
            categories = new ArrayList<>();
            error("Failed to load categories: " + e.getMessage());
        }
    }

    /**
     * Reloads the list of breeds from the server.
     * Applies the current category filter if set.
     */
    private void reloadBreeds() {
        try {
            String url = ADMIN_API_BASE + "/breeds";
            if (filterCategoryId != null) url += "?categoryId=" + filterCategoryId;
            String json = request("GET", url, null);
            breeds = JsonUtils.getObjectMapper()
                    .readValue(json, new TypeReference<List<BreedDTO>>() {});
        } catch (Exception e) {
            breeds = new ArrayList<>();
            error("Failed to load breeds: " + e.getMessage());
        }
    }

    // ======== HTTP helper methods ========

    /**
     * Makes an HTTP request to the backend API.
     * 
     * @param method The HTTP method (GET, POST, PUT, DELETE)
     * @param urlStr The URL to request
     * @param json The JSON payload (for POST/PUT) or null
     * @return The response body as a string
     * @throws IOException if the request fails
     */
    private String request(String method, String urlStr, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");

        // Add authentication if available
        String token = (loginBean != null) ? loginBean.getToken() : null;
        if (token != null && !token.isBlank()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }

        // For requests with a body (POST/PUT)
        if (json != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        
        // Read response
        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = readAll(is);
        
        // Throw exception for error status codes
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        return body;
    }

    /**
     * Reads all data from an InputStream into a String.
     * 
     * @param is The InputStream to read from
     * @return The contents of the stream as a String
     * @throws IOException if an I/O error occurs
     */
    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); 
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ======== Message helpers ========

    /**
     * Displays an error message to the user.
     * 
     * @param msg The error message to display
     */
    private void error(String msg) {
        FacesContext().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    /**
     * Displays an info message to the user.
     * 
     * @param msg The info message to display
     */
    private void info(String msg) {
        FacesContext().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    /**
     * Returns the current FacesContext instance.
     * 
     * @return The FacesContext instance
     */
    private jakarta.faces.context.FacesContext FacesContext() { 
        return jakarta.faces.context.FacesContext.getCurrentInstance(); 
    }

    // ======== Getters / Setters (needed by EL) ========

    /**
     * Returns the list of categories.
     * 
     * @return The list of categories
     */
    public List<CategoryDTO> getCategories() { 
        return categories; 
    }
    
    /**
     * Gets the currently selected category ID for filtering breeds.
     * 
     * @return The selected category ID, or null if no filter is applied
     */
    public Long getFilterCategoryId() { 
        return filterCategoryId; 
    }
    
    /**
     * Sets the category ID to filter breeds by.
     * 
     * @param filterCategoryId The category ID to filter by, or null to show all breeds
     */
    public void setFilterCategoryId(Long filterCategoryId) { 
        this.filterCategoryId = filterCategoryId; 
    }

    public List<BreedDTO> getBreeds() { return breeds; }
    public void setBreeds(List<BreedDTO> breeds) { this.breeds = breeds; }

    public BreedDTO getForm() { return form; }
    public void setForm(BreedDTO form) { this.form = form; }

    public boolean isEditing() { return editing; }
    public void setEditing(boolean editing) { this.editing = editing; }
}
