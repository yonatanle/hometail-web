package com.hometail.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hometail.web.dto.AdoptionRequestDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

/**
 * A view-scoped managed bean that handles the display and management of a user's adoption requests.
 * This bean is responsible for:
 * <ul>
 *   <li>Loading and displaying the current user's adoption requests</li>
 *   <li>Allowing users to delete their adoption requests</li>
 *   <li>Providing navigation to edit existing requests</li>
 *   <li>Displaying success/error messages for user actions</li>
 * </ul>
 * 
 * <p>The bean is view-scoped, meaning a new instance is created for each view and maintained
 * while the user interacts with the same view.</p>
 * 
 * <p><b>Example usage in JSF:</b></p>
 * <pre>
 * &lt;h:dataTable value="#{myAdoptionRequestsBean.requests}" var="request"&gt;
 *   &lt;h:column&gt;#{request.animalName}&lt;/h:column&gt;
 *   &lt;h:column&gt;
 *     &lt;h:commandButton value="Delete" 
 *                     action="#{myAdoptionRequestsBean.deleteRequest(request.id)}" /&gt;
 *   &lt;/h:column&gt;
 * &lt;/h:dataTable&gt;
 * </pre>
 * 
 * @see jakarta.faces.view.ViewScoped
 * @see AdoptionRequestDTO
 * @see LoginBean
 */
@Named("myAdoptionRequestsBean")
@ViewScoped
@Getter
@Setter
public class MyAdoptionRequestsBean implements Serializable {

    /**
     * The list of adoption requests for the current user.
     * This list is populated when the bean is initialized and can be refreshed by calling {@link #loadRequests()}.
     */
    private List<AdoptionRequestDTO> requests;

    /**
     * Injected LoginBean instance used for authentication and user context.
     * Provides the authentication token needed for API requests.
     */
    @Inject
    private LoginBean loginBean;
    
    /**
     * Initializes the bean by loading the current user's adoption requests.
     * This method is automatically called by the container after dependency injection is done.
     */
    @PostConstruct
    public void init() {
        loadRequests();
    }


    /**
     * Loads the current user's adoption requests from the backend API.
     * This method makes an authenticated GET request to the adoption-requests endpoint
     * and updates the requests list with the response data.
     * 
     * <p>If the request is successful, the requests list is updated with the retrieved data.
     * If an error occurs, an error message is added to the FacesContext.</p>
     * 
     * <p>This method is automatically called during bean initialization and can be called
     * to refresh the list of requests after modifications.</p>
     * 
     * @see #init()
     * @see #deleteRequest(Long)
     */
    public void loadRequests() {
        try {
            // Get authentication token from the login bean
            String token = loginBean.getToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("User is not authenticated");
            }

            // Prepare and send the HTTP request
            URL url = URI.create("http://localhost:9090/api/adoption-requests/my-requests").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);

            // Process the response
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream();
                     Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
                    // Read the response body as a string
                    String json = scanner.hasNext() ? scanner.next() : "";
                    
                    // Parse the JSON response into a list of AdoptionRequestDTO objects
                    requests = JsonUtils.getObjectMapper().readValue(
                        json, 
                        new TypeReference<List<AdoptionRequestDTO>>() {}
                    );
                }
            } else {
                // Handle non-OK response
                String errorMsg = "Failed to load adoption requests. Status: " + conn.getResponseCode();
                FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, errorMsg, null)
                );
            }
        } catch (Exception e) {
            // Log the error and show a user-friendly message
            e.printStackTrace();
            FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "An error occurred while loading your adoption requests. Please try again later.",
                    null
                )
            );
        }
    }

    /**
     * Deletes the specified adoption request from the system.
     * This method sends a DELETE request to the backend API to remove the adoption request
     * with the given ID. After a successful deletion, the requests list is refreshed.
     * 
     * <p>This method handles the following scenarios:
     * <ul>
     *   <li>Successful deletion (HTTP 200 or 204) - Shows success message and refreshes the list</li>
     *   <li>Unauthorized access (HTTP 401/403) - Shows appropriate error message</li>
     *   <li>Not found (HTTP 404) - Shows error message that the request doesn't exist</li>
     *   <li>Other errors - Shows generic error message</li>
     * </ul>
     * 
     * @param requestId The ID of the adoption request to delete
     * @throws IllegalStateException if the user is not authenticated
     * 
     * @see #loadRequests()
     */
    public void deleteRequest(Long requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("Request ID cannot be null");
        }
        
        try {
            // Get authentication token
            String token = loginBean.getToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("User is not authenticated");
            }
            
            // Prepare and send the DELETE request
            URI uri = new URI("http", null, "localhost", 9090, 
                           "/api/adoption-requests/" + requestId, null, null);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            
            int statusCode = conn.getResponseCode();
            
            // Handle the response
            if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
                // Success - refresh the list and show success message
                loadRequests();
                FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage("Adoption request deleted successfully.")
                );
            } else if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED || 
                      statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                // Authentication/authorization error
                FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage(
                        FacesMessage.SEVERITY_ERROR,
                        "You are not authorized to delete this request.",
                        null
                    )
                );
            } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Request not found
                FacesContext.getCurrentInstance().addMessage(
                    null,
                    new FacesMessage(
                        FacesMessage.SEVERITY_WARN,
                        "The specified adoption request could not be found.",
                        null
                    )
                );
            } else {
                // Other error
                throw new RuntimeException("Unexpected response status: " + statusCode);
            }
        } catch (Exception e) {
            // Log the error and show a user-friendly message
            e.printStackTrace();
            FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "An error occurred while deleting the adoption request: " + e.getMessage(),
                    null
                )
            );
        }
    }

 }
