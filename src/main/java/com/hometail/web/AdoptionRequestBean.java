/* =====  AdoptionRequestBean  ===== */
package com.hometail.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hometail.web.dto.AdoptionRequestDTO;
import com.hometail.web.dto.AnimalDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.annotation.ManagedProperty;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.Setter;

import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Managed bean for handling adoption request operations in the web application.
 * This bean manages the UI state and business logic for creating, updating, and
 * canceling adoption requests for animals.
 * 
 * <p>The bean is view-scoped, meaning it maintains its state for the duration
 * of a user's interaction with a single page.</p>
 * 
 */
@Named("adoptionRequestBean")
@ViewScoped
@Getter 
@Setter
public class AdoptionRequestBean implements Serializable {
    private static final long serialVersionUID = 1L;

    // ==========  Dependencies  ==========
    
    /** Injected LoginBean for authentication and user session management */
    @Inject
    private LoginBean loginBean;

    // ==========  View State  ==========
    
    /** 
     * The ID of the animal for which the adoption request is being made.
     * Injected from request parameter 'id'.
     */
    @ManagedProperty(value = "#{param.id}")
    private Long animalId;
    
    /** The animal DTO object containing details of the animal */
    private AnimalDTO animal;

    /** 
     * Flag indicating if the current user is the owner of the animal.
     * Determines which UI elements to show.
     */
    private boolean owner;
    
    /** 
     * Flag indicating if the current user has already sent an adoption request
     * for this animal.
     */
    private boolean hasSentRequest;
    
    /** 
     * Count of adoption requests for the current animal.
     * Only populated and used when viewing as the animal owner.
     */
    private int adoptionCount;

    /** 
     * The existing adoption request made by the current user for this animal.
     * Null if no request exists.
     */
    private AdoptionRequestDTO existingRequest;
    
    /** 
     * The note/message entered by the user in the adoption request form.
     * Bound to the textarea in the UI.
     */
    private String note;

    // ==========  Lifecycle Methods  ==========
    
    /**
     * Initializes the bean. This method is called by the container after dependency
     * injection is done and before the class is put into service.
     */
    @PostConstruct
    private void init() { 
        // No initialization that depends on animalId here
    }
    
    /**
     * Loads the animal data and checks the adoption request status.
     * This method should be called when the page loads to initialize the view state.
     * It performs the following actions:
     * 1. Validates the animal ID
     * 2. Loads the animal details
     * 3. For logged-in users, checks if they are the owner
     * 4. For owners, loads the adoption request count
     * 5. For non-owners, checks for existing adoption requests
     */
    public void onInitialLoad() {
        if (animalId == null) {
            showMessage(FacesMessage.SEVERITY_ERROR, "Missing or invalid animal id in URL.");
            return;
        }
        loadAnimal();

        if (loginBean.isLoggedIn() && loginBean.getUser() != null) {
            Long userId = loginBean.getUser().getId();
            owner = animal.getOwnerId() != null && animal.getOwnerId().equals(userId);
            if (owner) {
                adoptionCount = fetchAdoptionCountForAnimal(animalId);
            } else {
                existingRequest = fetchCurrentUserRequestForAnimal(animalId);
                hasSentRequest = existingRequest != null;
                if (hasSentRequest) {
                    note = existingRequest.getNote();
                }
            }
        }
    }

    // ==========  Public Actions  ==========
    
    /**
     * Submits a new adoption request for the current animal.
     * Validates user login, creates a new adoption request DTO, and sends it to the server.
     * Updates the UI state after successful submission.
     * 
     * @return null to stay on the same page, or a navigation outcome if redirecting
     */
    public String send() {
        if (!loginBean.isLoggedIn()) {
            showMessage(FacesMessage.SEVERITY_ERROR, "You need to log-in first.");
            return null;
        }
        
        try {
            // Create and populate the DTO
            AdoptionRequestDTO dto = new AdoptionRequestDTO();
            dto.setAnimalId(animalId);
            dto.setNote(note == null ? "" : note.trim());

            // Convert DTO to JSON
            String json = JsonUtils.getObjectMapper().writeValueAsString(dto);

            // Prepare HTTP request
            URL url = URI.create("http://localhost:9090/api/adoption-requests").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + loginBean.getToken());

            // Send request body
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            // Handle response
            if (con.getResponseCode() == 200 || con.getResponseCode() == 201) {
                showMessage(FacesMessage.SEVERITY_INFO, "Adoption request sent!");
                
                // Update UI state
                existingRequest = fetchCurrentUserRequestForAnimal(animalId);
                hasSentRequest = true;
                return null;  // Stay on the same page
            }
            
            showMessage(FacesMessage.SEVERITY_ERROR, 
                       "Server responded with HTTP " + con.getResponseCode());
                       
        } catch (Exception ex) {
            showMessage(FacesMessage.SEVERITY_ERROR, 
                       "Error sending adoption request: " + ex.getMessage());
        }
        return null;
    }

    // ==========  Helper Methods  ==========
    
    /**
     * Loads the animal details from the server.
     * Makes a GET request to the /api/animals/{id} endpoint.
     * Updates the animal property with the response data.
     * 
     * @throws RuntimeException if the animal cannot be loaded
     */
    private void loadAnimal() {
        try {
            URL url = URI.create("http://localhost:9090/api/animals/" + animalId).toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try (Scanner sc = new Scanner(con.getInputStream())) {
                String json = sc.useDelimiter("\\A").next();
                animal = JsonUtils.getObjectMapper().readValue(json, AnimalDTO.class);
            }
        } catch (Exception e) {
            String errorMsg = "Cannot load animal details: " + e.getMessage();
            showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Fetches the current user's adoption request for the specified animal.
     *
     * @param animalId The ID of the animal to check for requests
     * @return The adoption request DTO if found, null otherwise
     */
    private AdoptionRequestDTO fetchCurrentUserRequestForAnimal(Long animalId) {
        try {
            // Call the backend API to get all requests for the current user
            URL url = URI.create("http://localhost:9090/api/adoption-requests/my-requests").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + loginBean.getToken());

            if (con.getResponseCode() == 200) {
                try (Scanner sc = new Scanner(con.getInputStream())) {
                    // Parse the JSON response
                    String json = sc.useDelimiter("\\A").next();
                    List<AdoptionRequestDTO> allRequests = JsonUtils.getObjectMapper()
                            .readValue(json, new TypeReference<List<AdoptionRequestDTO>>() {});
                    
                    // Find the request for the current animal, if any
                    return allRequests.stream()
                            .filter(r -> r.getAnimalId().equals(animalId))
                            .findFirst()
                            .orElse(null);
                }
            } else {
                String errorMsg = "Failed to fetch adoption requests. Server responded with: " + con.getResponseCode();
                showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = "Error checking for existing request: " + e.getMessage();
            showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
        }
        return null;
    }

    /**
     * Fetches the number of adoption requests for the specified animal.
     * This method is only used by the animal owner.
     * 
     * @param animalId The ID of the animal to check
     * @return The number of adoption requests for the animal, or 0 if an error occurs
     */
    private int fetchAdoptionCountForAnimal(Long animalId) {
        try {
            // Call the backend API to get all requests for this animal
            URL url = URI.create("http://localhost:9090/api/adoption-requests/requests-for-my-animal/" + animalId).toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + loginBean.getToken());

            if (con.getResponseCode() == 200) {
                try (Scanner sc = new Scanner(con.getInputStream())) {
                    // Parse the JSON response and return the count
                    String json = sc.useDelimiter("\\A").next();
                    List<AdoptionRequestDTO> requests = JsonUtils.getObjectMapper()
                            .readValue(json, new TypeReference<List<AdoptionRequestDTO>>() {});
                    return requests.size();
                }
            } else {
                String errorMsg = "Failed to fetch adoption count. Server responded with: " + con.getResponseCode();
                showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = "Error loading adoption count: " + e.getMessage();
            showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
        }
        return 0;
    }

    /**
     * Updates the note of an existing adoption request.
     * Only the note field can be updated after the request is created.
     * 
     * @return null to stay on the same page
     */
    public String updateAdoptionRequest() {
        // Validate that there's an existing request to update
        if (!hasSentRequest || existingRequest == null) {
            showMessage(FacesMessage.SEVERITY_ERROR, "No request to update.");
            return null;
        }

        try {
            // Prepare the update payload with just the note field
            String json = JsonUtils.getObjectMapper().writeValueAsString(
                    Map.of("note", note == null ? "" : note.trim())
            );

            // Call the update endpoint
            URL url = URI.create("http://localhost:9090/api/adoption-requests/" + 
                            existingRequest.getId() + "/note").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("PUT");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + loginBean.getToken());

            // Send the request body
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            // Handle the response
            if (con.getResponseCode() == 200) {
                showMessage(FacesMessage.SEVERITY_INFO, "Adoption request updated successfully.");
                
                // Refresh the local data to ensure consistency
                existingRequest = fetchCurrentUserRequestForAnimal(
                        animalId);
                if (existingRequest != null) {
                    note = existingRequest.getNote();
                }
            } else {
                showMessage(FacesMessage.SEVERITY_ERROR, 
                           "Failed to update request. Server responded with: " + con.getResponseCode());
            }
        } catch (Exception e) {
            showMessage(FacesMessage.SEVERITY_ERROR, 
                       "Error updating adoption request: " + e.getMessage());
        }
        return null;
    }

    /**
     * Test method to verify the bean is properly initialized and working.
     * Displays a success message when called.
     * 
     * @return null to stay on the same page
     */
    public String testMethod() {
        showMessage(FacesMessage.SEVERITY_INFO, "Test method called successfully!");
        return null;
    }

    /**
     * Cancels the current user's adoption request for the animal.
     * After successful cancellation, redirects to the myAdoptionRequests page.
     * 
     * @return Navigation outcome for the myAdoptionRequests page, or null if cancellation fails
     */
    public String cancelRequest() {
        // Validate that there's an existing request to cancel
        if (!hasSentRequest || existingRequest == null) {
            showMessage(FacesMessage.SEVERITY_ERROR, "No request to cancel.");
            return null;
        }

        try {
            // Call the delete endpoint
            URL url = URI.create("http://localhost:9090/api/adoption-requests/" + existingRequest.getId()).toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("DELETE");
            con.setRequestProperty("Authorization", "Bearer " + loginBean.getToken());

            int code = con.getResponseCode();
            if (code == 200 || code == 204) {
                // Set up a flash message for the next page
                FacesContext ctx = FacesContext.getCurrentInstance();
                var flash = ctx.getExternalContext().getFlash();
                flash.setKeepMessages(true);   // Keep messages after redirect
                flash.setRedirect(true);
                ctx.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_INFO, 
                        "Adoption request cancelled successfully.", 
                        null));

                // Redirect to the myAdoptionRequests page
                return "myAdoptionRequests.xhtml?faces-redirect=true";
            } else {
                showMessage(FacesMessage.SEVERITY_ERROR, 
                           "Failed to cancel request. Server responded with: " + code);
            }
        } catch (Exception e) {
            showMessage(FacesMessage.SEVERITY_ERROR, 
                       "Error cancelling adoption request: " + e.getMessage());
        }

        return null; // Stay on the same page if there was an error
    }


    /**
     * Helper method to display a FacesMessage to the user.
     * 
     * @param severity The severity level of the message (e.g., INFO, ERROR)
     * @param message The message text to display
     */
    private void showMessage(FacesMessage.Severity severity, String message) {
        FacesContext.getCurrentInstance()
                .addMessage(null, new FacesMessage(severity, message, null));
    }


    /**
     * Deletes the current animal and all its associated adoption requests.
     * Only the owner of the animal can perform this action.
     * After successful deletion, redirects to the myAnimals page.
     * 
     * @return Navigation outcome for the myAnimals page, or null if deletion fails
     */
    public String deleteAnimal() {
        try {
            // Get the authentication token
            String token = loginBean.getToken();
            Long animalId = this.animal != null ? this.animal.getId() : this.animalId;
            
            if (animalId == null) {
                showMessage(FacesMessage.SEVERITY_ERROR, "No animal selected for deletion.");
                return null;
            }

            // Prepare the DELETE request
            URL url = URI.create("http://localhost:9090/api/animals/" + animalId).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bearer " + token);

            // Execute the request
            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                // Set up flash message for the next page
                FacesContext context = FacesContext.getCurrentInstance();
                context.getExternalContext().getFlash().setKeepMessages(true);

                // Show success message
                showMessage(FacesMessage.SEVERITY_INFO, 
                           "Animal and all related adoption requests were successfully deleted.");

                // Redirect to the myAnimals page
                return "myAnimals.xhtml?faces-redirect=true";
            } else {
                // Handle error response
                String errorMsg = "Failed to delete animal. Server responded with code: " + responseCode;
                showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
                return null;
            }
        } catch (Exception e) {
            // Log the error and show a user-friendly message
            String errorMsg = "An error occurred while trying to delete the animal: " + e.getMessage();
            showMessage(FacesMessage.SEVERITY_ERROR, errorMsg);
            e.printStackTrace();
            return null;
        }
    }

}
