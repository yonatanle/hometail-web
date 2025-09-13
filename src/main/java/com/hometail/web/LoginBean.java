package com.hometail.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hometail.web.dto.UserDTO;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ComponentSystemEvent;
import jakarta.inject.Named;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

/**
 * A session-scoped managed bean that handles user authentication and session management.
 * This bean is responsible for:
 * <ul>
 *   <li>User login/logout functionality</li>
 *   <li>Session management</li>
 *   <li>Role-based access control</li>
 *   <li>User information storage during the session</li>
 *   <li>Navigation control based on authentication state</li>
 * </ul>
 * 
 * <p>The bean is session-scoped, meaning each user gets their own instance that persists
 * throughout their session. It stores the authentication token and user details after successful login.</p>
 * 
 * <p><b>Example usage in JSF:</b></p>
 * <pre>
 * &lt;h:form>
 *   &lt;h:inputText value="#{loginBean.email}" />
 *   &lt;h:inputSecret value="#{loginBean.password}" />
 *   &lt;h:commandButton value="Login" action="#{loginBean.login}" />
 * &lt;/h:form>
 * </pre>
 * 
 * @see jakarta.enterprise.context.SessionScoped
 * @see UserDTO
 */
@Named("loginBean")
@SessionScoped
public class LoginBean implements Serializable {
    private static final long serialVersionUID = 1L;

    // User credentials (from login form)
    private String email;
    private String password;
    
    // Session data
    private String token;        // JWT token received after successful authentication
    private UserDTO user;        // Authenticated user's details
    private String errorMessage; // Error message to display on login failure

    @PostConstruct
    public void init() {
        // Clear any existing error messages when the login page is loaded
        this.errorMessage = null;
    }


    /**
     * Handles the pre-render view event to redirect authenticated users to the home page.
     * This method is typically used with f:event in the welcome page to ensure
     * logged-in users are automatically redirected to the home page.
     * 
     * @param event The ComponentSystemEvent that triggered this method
     */
    public void redirectIfLoggedIn(ComponentSystemEvent event) {
        if (isLoggedIn()) {
            try {
                ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();
                ec.redirect(ec.getRequestContextPath() + "/home.xhtml");
            } catch (IOException e) {
                // Log the error if redirection fails
                System.err.println("Error during redirect: " + e.getMessage());
            }
        }
    }

    /**
     * Attempts to authenticate the user with the provided email and password.
     * On successful authentication, stores the received JWT token and user details in the session.
     * 
     * <p>This method:
     * <ol>
     *   <li>Sends a POST request to the authentication endpoint</li>
     *   <li>Processes the response to extract the JWT token and user details</li>
     *   <li>Updates the session state with the authentication results</li>
     *   <li>Returns the appropriate navigation outcome</li>
     * </ol>
     * 
     * @return Navigation outcome string (URL to redirect to), or null to stay on the same page
     * @see #logout()
     */
    public String login() {
        HttpURLConnection conn = null;
        try {
            // Prepare the authentication request
            URL url = new URL("http://localhost:9090/api/auth/login");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Create and send the login payload
            String payload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            // Process the response
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // Successful login - parse the response
                try (Scanner sc = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    String response = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
                    JsonNode root = JsonUtils.getObjectMapper().readTree(response);

                    // Store the authentication token and user details
                    this.token = root.path("token").asText(null);
                    this.user = JsonUtils.getObjectMapper()
                            .treeToValue(root.path("user"), UserDTO.class);
                    this.errorMessage = null;
                }
                // Navigate to home page after successful login
                return "/home.xhtml?faces-redirect=true";
            } else {
                // Authentication failed
                this.errorMessage = "Invalid email or password.";
                return null; // Stay on the login page
            }
        } catch (Exception e) {
            // Handle any errors during the login process
            this.errorMessage = "Login failed: " + e.getMessage();
            return null;
        } finally {
            // Clean up the connection
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Logs out the current user by invalidating the session and clearing user data.
     * 
     * <p>This method:
     * <ol>
     *   <li>Invalidates the current HTTP session</li>
     *   <li>Clears the authentication token and user data</li>
     *   <li>Redirects to the login page</li>
     * </ol>
     * 
     * @return Navigation outcome string that redirects to the login page
     * @see #login()
     */
    public String logout() {
        // Invalidate the current session
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        
        // Clear user data
        this.token = null;
        this.user = null;
        
        // Redirect to login page
        return "/login.xhtml?faces-redirect=true";
    }

    /**
     * Redirects to the login page if the user is not logged in.
     * This method is typically used as a filter in page controllers or filters.
     * 
     * @throws IOException if the redirect fails
     * @see #isLoggedIn()
     * @see #redirectIfAlreadyLoggedIn()
     */
    public void redirectIfNotLoggedIn() throws IOException {
        if (!isLoggedIn()) {
            ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();
            ec.redirect(ec.getRequestContextPath() + "/login.xhtml");
        }
    }

    /**
     * Checks if the currently logged-in user has the specified role.
     * The check is case-insensitive and handles both "ROLE_" prefixed and unprefixed role names.
     * 
     * <p>Examples:
     * <ul>
     *   <li><code>hasRole("admin")</code> will match both "ADMIN" and "ROLE_ADMIN"</li>
     *   <li><code>hasRole("ROLE_USER")</code> will match both "USER" and "ROLE_USER"</li>
     * </ul>
     * 
     * @param role The role to check for (case-insensitive, with or without "ROLE_" prefix)
     * @return true if the user has the specified role, false otherwise
     * @see #isAdmin()
     */
    public boolean hasRole(String role) {
        if (user == null || role == null) return false;
        
        // Normalize the requested role (trim, uppercase, remove ROLE_ prefix if present)
        String wanted = role.trim().toUpperCase(Locale.ROOT);
        
        // Get the user's role from the DTO
        String userRole = user.getRole();
        if (userRole == null) return false;
        
        // Normalize the user's role (trim and uppercase)
        String have = userRole.trim().toUpperCase(Locale.ROOT);
        
        // Check for exact match or match with/without ROLE_ prefix
        return have.equals(wanted) || 
               have.equals("ROLE_" + wanted) || 
               (wanted.startsWith("ROLE_") && have.equals(wanted.substring(5)));
    }

    /**
     * Checks if a user is currently logged in by verifying the presence of a valid token.
     * 
     * @return true if a user is logged in (has a non-blank token), false otherwise
     * @see #getToken()
     */
    public boolean isLoggedIn() { 
        return token != null && !token.isBlank(); 
    }

    /**
     * Checks if the currently logged-in user has administrator privileges.
     * This is a convenience method equivalent to <code>hasRole("ADMIN")</code>.
     * 
     * @return true if the user is an administrator, false otherwise
     * @see #hasRole(String)
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Redirects users who are not administrators to the home page.
     * If the user is not logged in, redirects to the login page instead.
     * 
     * <p>This is typically used to protect admin-only pages by redirecting
     * unauthorized users to a safe location.</p>
     * 
     * @throws IOException if the redirect fails
     * @see #isAdmin()
     * @see #redirectIfNotLoggedIn()
     */
    public void redirectIfNotAdmin() throws IOException {
        var ec = FacesContext.getCurrentInstance().getExternalContext();
        if (!isLoggedIn()) {
            // Not logged in - go to login page
            ec.redirect(ec.getRequestContextPath() + "/login.xhtml");
            return;
        }
        if (!isAdmin()) {
            // Logged in but not an admin - go to home page
            ec.redirect(ec.getRequestContextPath() + "/home.xhtml");
        }
    }

    /**
     * Redirects to the home page if the user is already logged in.
     * This is typically used on the login page to prevent authenticated users
     * from accessing the login form.
     * 
     * @throws IOException if the redirect fails
     * @see #isLoggedIn()
     * @see #redirectIfNotLoggedIn()
     */
    public void redirectIfAlreadyLoggedIn() throws IOException {
        if (isLoggedIn()) {
            var ec = FacesContext.getCurrentInstance().getExternalContext();
            ec.redirect(ec.getRequestContextPath() + "/home.xhtml");
        }
    }

    // ---------------------------------------------------------------------
    // Utility Getters
    // ---------------------------------------------------------------------
    
    /**
     * Gets the full name of the currently logged-in user.
     * 
     * @return The user's full name, or an empty string if not logged in
     */
    public String getFullName() { 
        return user != null ? user.getFullName() : ""; 
    }
    
    /**
     * Gets the ID of the currently logged-in user.
     * 
     * @return The user's ID, or null if not logged in
     */
    public Long getUserId() { 
        return user != null ? user.getId() : null; 
    }

    // ---------------------------------------------------------------------
    // Standard Getters and Setters
    // ---------------------------------------------------------------------
    
    /** @return The email address used for login */
    public String getEmail() { 
        return email; 
    }
    
    /** @param email The email address to set */
    public void setEmail(String email) { 
        this.email = email; 
    }
    
    /** @return The user's password (only available during login) */
    public String getPassword() { 
        return password; 
    }
    
    /** @param password The password to set */
    public void setPassword(String password) { 
        this.password = password; 
    }
    
    /** 
     * Gets the authentication token for the current session.
     * @return The JWT token, or null if not logged in
     */
    public String getToken() { 
        return token; 
    }
    
    /** @param token The authentication token to set */
    public void setToken(String token) { 
        this.token = token; 
    }
    
    /** 
     * Gets the currently logged-in user's details.
     * @return The UserDTO containing user details, or null if not logged in
     */
    public UserDTO getUser() { 
        return user; 
    }
    
    /** @param user The UserDTO to set */
    public void setUser(UserDTO user) { 
        this.user = user; 
    }
    
    /** 
     * Gets the last error message that occurred during login.
     * @return The error message, or null if no error occurred
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /** @param errorMessage The error message to set */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * Displays any flash message that was set during a redirect.
     * This method is called during the pre-render phase of the view.
     * It shows a single, clean success message without duplicates.
     */
    public void showFlashMessage() {
        FacesContext context = FacesContext.getCurrentInstance();
        ExternalContext externalContext = context.getExternalContext();
        
        // Check for success message in flash scope
        String successMessage = (String) externalContext.getFlash().get("successMessage");
        
        // If we have a success message, add it to the faces context
        if (successMessage != null && !successMessage.trim().isEmpty()) {
            // Add a single message to the context
            context.addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO, 
                "Success", 
                successMessage
            ));
            
            // Clear the message from flash to prevent it from showing again on refresh
            externalContext.getFlash().put("successMessage", null);
            externalContext.getFlash().put("message", null);
        }
    }
}
