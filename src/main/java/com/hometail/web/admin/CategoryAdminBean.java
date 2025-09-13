package com.hometail.web.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hometail.web.LoginBean;
import com.hometail.web.dto.CategoryDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean for managing categories in the admin interface.
 * Handles CRUD operations for categories including creation, editing, and deletion.
 * Communicates with the backend API to persist category data.
 * 
 */
@Named("categoryAdminBean")
@ViewScoped
public class CategoryAdminBean implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Base URL for category admin API endpoints */
    private static final String ADMIN_API_PATH = "http://localhost:9090/api/admin/categories";
    
    /** Injected login bean for authentication */
    @Inject
    private LoginBean loginBean;

    /** JSON mapper for serialization/deserialization */
    private transient JsonMapper mapper;
    
    /** List of all categories */
    private List<CategoryDTO> categories = new ArrayList<>();
    
    
    /**
     * Gets the list of all categories.
     * 
     * @return The list of categories
     */
    public List<CategoryDTO> getCategories() {
        return categories;
    }
    
    /**
     * Sets the list of categories.
     * 
     * @param categories The list of categories to set
     */
    public void setCategories(List<CategoryDTO> categories) {
        this.categories = categories != null ? categories : new ArrayList<>();
    }
    
    /** Current category being edited/created */
    private CategoryDTO form = new CategoryDTO();
    
    /**
     * Gets the current category form.
     * 
     * @return The current category form
     */
    public CategoryDTO getForm() {
        return form;
    }
    
    /**
     * Sets the current category form.
     * 
     * @param form The category form to set
     */
    public void setForm(CategoryDTO form) {
        this.form = form != null ? form : new CategoryDTO();
    }
    
    /** Flag indicating if we're in edit mode (true) or create mode (false) */
    private boolean editing = false;
    
    /**
     * Gets the editing state.
     * 
     * @return true if in edit mode, false if in create mode
     */
    public boolean isEditing() {
        return editing;
    }
    
    /**
     * Sets the editing state.
     * 
     * @param editing true for edit mode, false for create mode
     */
    public void setEditing(boolean editing) {
        this.editing = editing;
    }
    
    /** ID of the category to be deleted */
    private Long toDeleteId;

    /**
     * Initializes the bean by setting up the JSON mapper and loading categories.
     * Called automatically after dependency injection is done.
     */
    @PostConstruct
    public void init() {
        initializeMapper();
        reload();
    }

    /**
     * Initializes the JSON mapper with custom configuration if it hasn't been initialized yet.
     * Disables timestamp serialization and unknown property failures.
     */
    private void initializeMapper() {
        if (mapper == null) {
            mapper = JsonMapper.builder()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
        }
    }

    /**
     * Reloads the list of categories from the server.
     * Displays an error message if the operation fails.
     */
    public void reload() {
        try {
            initializeMapper(); // Ensure mapper is initialized
            String body = httpGet(ADMIN_API_PATH);
            categories = mapper.readValue(body, new TypeReference<List<CategoryDTO>>() {});
        } catch (Exception e) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Load failed", e.getMessage());
            categories = new ArrayList<>();
        }
    }

    /**
     * Prepares the form for creating a new category.
     * Sets up a new empty category with default values.
     */
    public void startCreate() {
        editing = false;
        form = new CategoryDTO();
        form.setActive(true);
    }

    /**
     * Prepares the form for editing an existing category.
     * 
     * @param c The category to edit
     */
    public void startEdit(CategoryDTO c) {
        editing = true;
        form = new CategoryDTO();
        form.setId(c.getId());
        form.setName(c.getName());
        form.setActive(c.isActive());
        form.setSortOrder(c.getSortOrder());
    }


    /**
     * Saves the current category (create or update).
     * Performs validation and communicates with the backend API.
     */
    public void save() {
        try {
            // Basic validation
            if (form.getName() == null || form.getName().isBlank()) {
                addMsg(FacesMessage.SEVERITY_WARN, "Validation", "Name is required");
                return;
            }
            
            initializeMapper(); // Ensure mapper is initialized
            String url = ADMIN_API_PATH + (editing ? ("/" + form.getId()) : "");
            String json = mapper.writeValueAsString(form);
            
            // Choose between PUT (update) or POST (create) based on edit mode
            String resp = editing ? httpPut(url, json) : httpPost(url, json);
            CategoryDTO saved = mapper.readValue(resp, CategoryDTO.class);
            
            addMsg(FacesMessage.SEVERITY_INFO, "Saved", "Category " + saved.getName());
            reload();
        } catch (Exception e) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Save failed", e.getMessage());
        }
    }

    /**
     * Prepares a category for deletion by storing its ID.
     * Should be called before showing the delete confirmation dialog.
     * 
     * @param dto The category to be deleted
     */
    public void confirmDelete(CategoryDTO dto) {
        toDeleteId = dto.getId();
        // Dialog will be shown via oncomplete in XHTML
    }

    /**
     * Deletes the currently selected category after confirmation.
     * Reloads the category list after successful deletion.
     */
    public void deleteSelected() {
        if (toDeleteId == null) return;
        try {
            httpDelete(ADMIN_API_PATH + "/" + toDeleteId);
            addMsg(FacesMessage.SEVERITY_INFO, "Deleted", "Category #" + toDeleteId);
            reload();
        } catch (Exception e) {
            addMsg(FacesMessage.SEVERITY_ERROR, "Delete failed", e.getMessage());
        } finally {
            toDeleteId = null;
        }
    }

    // ======== HTTP helper methods ========
    
    /**
     * Performs an HTTP GET request.
     * 
     * @param url The URL to request
     * @return The response body
     * @throws IOException if the request fails
     */
    private String httpGet(String url) throws IOException { 
        return request("GET", url, null); 
    }
    
    /**
     * Performs an HTTP POST request with JSON payload.
     * 
     * @param url The URL to request
     * @param json The JSON payload to send
     * @return The response body
     * @throws IOException if the request fails
     */
    private String httpPost(String url, String json) throws IOException { 
        return request("POST", url, json); 
    }
    
    /**
     * Performs an HTTP PUT request with JSON payload.
     * 
     * @param url The URL to request
     * @param json The JSON payload to send
     * @return The response body
     * @throws IOException if the request fails
     */
    private String httpPut(String url, String json) throws IOException { 
        return request("PUT", url, json); 
    }
    
    /**
     * Performs an HTTP DELETE request.
     * 
     * @param url The URL to request
     * @return The response body
     * @throws IOException if the request fails
     */
    private String httpDelete(String url) throws IOException { 
        return request("DELETE", url, null); 
    }

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
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
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
            throw new IOException("HTTP " + code + " " + method + " " + urlStr + " -> " + body);
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
    static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(2048);
            String line; 
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Adds a Faces message to be displayed to the user.
     * 
     * @param severity The severity level of the message
     * @param summary The summary/title of the message
     * @param detail The detailed message
     */
    private void addMsg(FacesMessage.Severity severity, String summary, String detail) {
        jakarta.faces.context.FacesContext.getCurrentInstance()
            .addMessage(null, new FacesMessage(severity, summary, detail));
    }
    
    /**
     * Executes JavaScript code on the client side.
     * 
     * @param js The JavaScript code to execute
     */
    private void runJs(String js) {
        org.primefaces.PrimeFaces.current().executeScript(js);
    }
}
