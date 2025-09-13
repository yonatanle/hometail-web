package com.hometail.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hometail.web.dto.AdoptionRequestDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.annotation.ManagedProperty;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * Managed bean for handling the display and management of adoption requests for a specific animal.
 * This view-scoped bean provides functionality for animal owners to view, approve, and reject
 * adoption requests made for their listed animals.
 * 
 * <p>Key features include:
 * <ul>
 *   <li>Loading all adoption requests for a specific animal</li>
 *   <li>Approving or rejecting pending adoption requests</li>
 *   <li>Displaying request details in a user interface</li>
 *   <li>Providing feedback through JSF messages</li>
 * </ul>
 * 
 */

@Named("adoptionRequestsForAnimalBean")
@ViewScoped
@Getter
@Setter
public class AdoptionRequestsForAnimalBean implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Injected LoginBean for authentication and user session management */
    @Inject
    private LoginBean loginBean;
    
    /** Currently selected adoption request for operations */
    private AdoptionRequestDTO selectedRequest;
    
    /** ID of the animal for which requests are being managed */
    @ManagedProperty(value = "#{param.id}")
    private Long animalId;
    
    /** List of adoption requests for the current animal */
    private List<AdoptionRequestDTO> requests;
    
    /** Action to be performed (approve/reject) on the selected request */
    private String pendingAction;

    /**
     * Initializes the bean after construction. This method is automatically called by the container.
     * It performs the following operations:
     * 1. Attempts to get the animalId from URL parameters
     * 2. Falls back to getting it from the parent view bean if not found in URL
     * 3. Verifies the current user is the owner of the animal
     * 4. Loads adoption requests if all validations pass
     */
    @PostConstruct
    public void init() {
        FacesContext ctx = FacesContext.getCurrentInstance();

        // Try to get animalId from URL parameters first
        if (animalId == null) {
            String idParam = ctx.getExternalContext().getRequestParameterMap().get("id");
            if (idParam != null && !idParam.isEmpty()) {
                try { 
                    animalId = Long.valueOf(idParam); 
                } catch (NumberFormatException ignore) {
                    // Silently handle invalid ID format
                }
            }
        }

        // Fallback: Try to get animalId from the parent view bean
        if (animalId == null) {
            try {
                animalId = ctx.getApplication()
                        .evaluateExpressionGet(ctx, "#{adoptionRequestBean.animalId}", Long.class);
            } catch (Exception ignore) {
                // Silently handle evaluation errors
            }
        }

        // Verify if the current user is the owner of the animal
        Boolean isOwner = false;
        try {
            isOwner = ctx.getApplication()
                    .evaluateExpressionGet(ctx, "#{adoptionRequestBean.owner}", Boolean.class);
        } catch (Exception ignored) {
            // Silently handle evaluation errors
        }

        // Only proceed if the current user is the owner
        if (Boolean.FALSE.equals(isOwner)) {
            return; // Not the owner, don't load anything
        }

        // Validate that we have an animalId
        if (animalId == null) {
            if (!ctx.isPostback()) {
                showMessage(FacesMessage.SEVERITY_ERROR, "Animal ID not found in URL or session");
            }
            return;
        }

        // Load adoption requests for the current animal
        loadRequests();
    }


    /**
     * Loads all adoption requests for the current animal from the backend API.
     * This method makes an authenticated GET request to fetch the requests
     * and updates the 'requests' list with the response data.
     */
    public void loadRequests() {
        try {
            String token = loginBean.getToken();
            String apiUrl = "http://localhost:9090/api/adoption-requests/requests-for-my-animal/" + animalId;
            
            // Set up HTTP connection
            URL url = URI.create(apiUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");

            // Process the response
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Read and parse the JSON response
                try (Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A")) {
                    String json = scanner.hasNext() ? scanner.next() : "";
                    requests = JsonUtils.getObjectMapper()
                            .readValue(json, new TypeReference<List<AdoptionRequestDTO>>() {});
                }
            } else {
                showMessage(FacesMessage.SEVERITY_ERROR, 
                    "Failed to load adoption requests. Server returned: HTTP " + responseCode);
            }
        } catch (Exception e) {
            showMessage(FacesMessage.SEVERITY_ERROR, 
                "Error loading adoption requests: " + e.getMessage());
        }
    }
    
    /**
     * Approves the currently selected adoption request.
     * Delegates to updateRequestStatus with status "APPROVED".
     */
    public void approve() {
        updateRequestStatus("APPROVED");
    }

    /**
     * Rejects the currently selected adoption request.
     * Delegates to updateRequestStatus with status "REJECTED".
     */
    public void reject() {
        // for debugging
//        if (selectedRequest != null) {
//            FacesContext.getCurrentInstance().addMessage(null,
//                new FacesMessage(FacesMessage.SEVERITY_INFO, "Processing rejection",
//                "Processing request ID: " + selectedRequest.getId()));
//        }
        updateRequestStatus("REJECTED");
    }

    /**
     * Updates the status of the currently selected adoption request.
     * 
     * @param newStatus The new status to set (APPROVED/REJECTED)
     */
    private void updateRequestStatus(String newStatus) {
        System.out.println("updateRequestStatus called with status: " + newStatus);
        System.out.println("Selected request: " + (selectedRequest != null ? selectedRequest.getId() : "null"));
        // Validate selected request
        if (selectedRequest == null || selectedRequest.getId() == null) {
            showMessage(FacesMessage.SEVERITY_ERROR, "No adoption request selected.");
            return;
        }
        
        // Ensure the request is in a modifiable state
        if (!"PENDING".equals(selectedRequest.getStatus())) {
            showMessage(FacesMessage.SEVERITY_ERROR, 
                "Cannot modify request: Only pending requests can be approved or rejected.");
            return;
        }
        
        try {
            // Prepare the API request
            String encodedStatus = URLEncoder.encode(newStatus, StandardCharsets.UTF_8.name());
            String apiUrl = "http://localhost:9090/api/adoption-requests/"
                    + selectedRequest.getId() + "/status?status=" + encodedStatus;
            
            // Set up and execute the HTTP PUT request
            HttpURLConnection connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Authorization", "Bearer " + loginBean.getToken());
            connection.setDoOutput(true);

            // Process the response
            int responseCode = connection.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                // Refresh the requests list to show updated status
                loadRequests();
                
                // Show success message to the user
                String actionText = "APPROVED".equals(newStatus) ? "approved" : "rejected";
                String candidateName = selectedRequest.getRequesterName();
                showMessage(FacesMessage.SEVERITY_INFO, 
                    String.format("Adoption request from %s has been %s successfully!", 
                                 candidateName, actionText));
            } else {
                showMessage(FacesMessage.SEVERITY_ERROR, 
                    "Failed to update request status. Please try again later.");
            }
        } catch (Exception e) {
            showMessage(FacesMessage.SEVERITY_ERROR, 
                "An error occurred while updating the request status: " + e.getMessage());
        }
    }

    /**
     * Placeholder method for displaying a message dialog.
     * Currently not implemented.
     * 
     * @return null to stay on the same page
     */
    public String message() {
        // TODO: Implement message dialog logic if needed
        return null;
    }

    /**
     * Displays a message to the user using JSF message system.
     * 
     * @param severity The severity level of the message (INFO, WARN, ERROR, etc.)
     * @param message The message text to display
     */
    private void showMessage(FacesMessage.Severity severity, String message) {
        if (message != null && !message.trim().isEmpty()) {
            FacesContext.getCurrentInstance()
                    .addMessage(null, new FacesMessage(severity, message, null));
        }
    }

}
