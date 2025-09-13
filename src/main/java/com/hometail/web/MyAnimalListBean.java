package com.hometail.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hometail.web.dto.AnimalDTO;
import jakarta.annotation.PostConstruct;
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
import java.util.*;

/**
 * A view-scoped managed bean that manages the list of animals owned by the current user.
 * This bean is responsible for:
 * <ul>
 *   <li>Loading and displaying animals belonging to the current user</li>
 *   <li>Tracking and displaying the count of pending adoption requests for each animal</li>
 *   <li>Providing methods to refresh the animal list and request counts</li>
 * </ul>
 * 
 * <p>The bean is view-scoped, meaning a new instance is created for each view and maintained
 * while the user interacts with the same view.</p>
 * 
 * <p><b>Example usage in JSF:</b></p>
 * <pre>
 * &lt;h:dataTable value="#{myAnimalListBean.myAnimals}" var="animal"&gt;
 *   &lt;h:column&gt;#{animal.name}&lt;/h:column&gt;
 *   &lt;h:column&gt;Pending requests: #{myAnimalListBean.pendingCounts[animal.id]}&lt;/h:column&gt;
 * &lt;/h:dataTable&gt;
 * </pre>
 * 
 * @see jakarta.faces.view.ViewScoped
 * @see AnimalDTO
 * @see LoginBean
 */
@Named("myAnimalListBean")
@ViewScoped
@Getter
@Setter
public class MyAnimalListBean implements Serializable {

    /**
     * List of animals owned by the current user.
     * Populated during initialization and can be refreshed by calling {@link #loadMyAnimals()}.
     */
    private List<AnimalDTO> myAnimals;

    /**
     * Map that stores the count of pending adoption requests for each animal.
     * The key is the animal ID, and the value is the count of pending requests.
     * Populated by {@link #preloadPendingCounts()} and updated as needed.
     */
    private Map<Long, Integer> pendingCounts = new HashMap<>();

    /**
     * Injected LoginBean instance used for authentication and user context.
     * Provides the authentication token and user ID needed for API requests.
     */
    @Inject
    private LoginBean loginBean;

    /**
     * Initializes the bean by loading the current user's animals and their pending request counts.
     * This method is automatically called by the container after dependency injection is done.
     * 
     * @see #loadMyAnimals()
     * @see #preloadPendingCounts()
     */
    @PostConstruct
    public void init() {
        loadMyAnimals();
        preloadPendingCounts();
    }

    /**
     * Preloads the count of pending adoption requests for all of the user's animals.
     * This method iterates through the user's animals and populates the pendingCounts map
     * with the count of pending requests for each animal.
     * 
     * <p>This method is automatically called during initialization and can be called
     * to refresh the pending request counts.</p>
     * 
     * @see #fetchPendingCount(Long)
     * @see #pendingCounts
     */
    private void preloadPendingCounts() {
        if (myAnimals == null) return;
        for (AnimalDTO animal : myAnimals) {
            pendingCounts.put(animal.getId(), fetchPendingCount(animal.getId()));
        }
    }

    /**
     * Fetches the count of pending adoption requests for a specific animal.
     * This method makes an authenticated GET request to the backend API to retrieve
     * the count of pending adoption requests for the specified animal.
     * 
     * @param animalId The ID of the animal to get the pending request count for
     * @return The count of pending adoption requests, or 0 if an error occurs
     * @throws IllegalArgumentException if animalId is null
     * 
     * @see #preloadPendingCounts()
     */
    private int fetchPendingCount(Long animalId) {
        if (animalId == null) {
            throw new IllegalArgumentException("Animal ID cannot be null");
        }
        
        try {
            // Prepare and send the HTTP request
            URL url = URI.create("http://localhost:9090/api/adoption-requests/animal/" + animalId + "/pending/count").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            // Add authentication header
            String token = loginBean.getToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("User is not authenticated");
            }
            conn.setRequestProperty("Authorization", "Bearer " + token);
            
            // Process the response
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (Scanner scanner = new Scanner(conn.getInputStream())) {
                    String body = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "0";
                    return Integer.parseInt(body.trim());
                }
            } else {
                // Log error for debugging
                System.err.println("Failed to fetch pending count for animal " + animalId + 
                                 ". Status: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            // Log the error and return 0
            System.err.println("Error fetching pending count for animal " + animalId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return 0; // Default to 0 on error
    }

    /**
     * Gets the count of pending adoption requests for a specific animal.
     * 
     * @param animalId The ID of the animal to get the pending request count for
     * @return The count of pending adoption requests, or 0 if no count is available
     * 
     * @see #preloadPendingCounts()
     * @see #fetchPendingCount(Long)
     */
    public Integer getPendingCount(Long animalId) {
        if (animalId == null) {
            return 0;
        }
        return pendingCounts.getOrDefault(animalId, 0);
    }

    /**
     * Loads the list of animals owned by the current user from the backend API.
     * This method makes an authenticated GET request to retrieve all animals
     * associated with the current user's account.
     * 
     * <p>The loaded animals are stored in the {@link #myAnimals} list and can be
     * accessed through the corresponding getter method.</p>
     * 
     * <p>If the request is successful, the method updates the internal list of animals.
     * If an error occurs, the error is logged and the internal list is set to an empty list.</p>
     * 
     * @throws IllegalStateException if the user is not authenticated
     * 
     * @see #init()
     * @see #myAnimals
     */
    public void loadMyAnimals() {
        try {
            // Get user ID and authentication token
            Long userId = loginBean.getUserId();
            String token = loginBean.getToken();
            
            if (userId == null) {
                throw new IllegalStateException("User ID is not available");
            }
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("User is not authenticated");
            }
            
            // Prepare and send the HTTP request
            URL url = URI.create("http://localhost:9090/api/animals/by-owner/" + userId).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);

            // Process the response
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream();
                     Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
                    // Read the response body as a string
                    String json = scanner.hasNext() ? scanner.next() : "";

                    // Parse the JSON response into a list of AnimalDTO objects
                    myAnimals = JsonUtils.getObjectMapper().readValue(
                        json, 
                        new TypeReference<List<AnimalDTO>>() {}
                    );
                    
                    // Ensure the list is never null
                    if (myAnimals == null) {
                        myAnimals = new ArrayList<>();
                    }
                }
            } else {
                // Log error for debugging
                System.err.println("Failed to load animals. Status: " + conn.getResponseCode());
                myAnimals = new ArrayList<>(); // Ensure we have an empty list on error
            }
        } catch (Exception e) {
            // Log the error and ensure we have an empty list
            System.err.println("Error loading animals: " + e.getMessage());
            e.printStackTrace();
            myAnimals = new ArrayList<>();
        }
    }
}
