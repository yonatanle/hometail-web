package com.hometail.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import lombok.Data;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * A request-scoped managed bean that handles user registration functionality.
 * This bean is responsible for:
 * <ul>
 *   <li>Collecting user registration information through a form</li>
 *   <li>Validating user input (e.g., password confirmation)</li>
 *   <li>Submitting registration data to the backend API</li>
 *   <li>Handling registration success/failure scenarios</li>
 *   <li>Redirecting authenticated users away from the registration page</li>
 * </ul>
 * 
 * <p>The bean is request-scoped, meaning a new instance is created for each HTTP request.</p>
 * 
 * <p><b>Example usage in JSF:</b></p>
 * <pre>
 * &lt;h:form&gt;
 *   &lt;h:inputText value="#{registerBean.firstName}" required="true" /&gt;
 *   &lt;h:inputSecret value="#{registerBean.password}" required="true" /&gt;
 *   &lt;h:commandButton value="Register" action="#{registerBean.register}" /&gt;
 * &lt;/h:form&gt;
 * </pre>
 * 
 * @see jakarta.enterprise.context.RequestScoped
 * @see jakarta.inject.Named
 */
@Data
@Named("registerBean")
@RequestScoped
public class RegisterBean implements Serializable {

    /** User's first name. Required field. */
    private String firstName;
    
    /** User's last name. Required field. */
    private String lastName;
    
    /** User's email address. Used as the primary account identifier. */
    private String email;
    
    /** User's password. Will be hashed before storage. */
    private String password;
    
    /** Confirmation of the user's password. Must match the password field. */
    private String confirmPassword;
    
    /** User's phone number. Optional field. */
    private String phoneNumber;
    
    /** General message to display to the user, typically for error or success messages. */
    private String message;

    /**
     * Processes the user registration form submission.
     * This method:
     * <ol>
     *   <li>Validates that the password and confirmation match</li>
     *   <li>Sends the registration data to the backend API</li>
     *   <li>Handles the response and provides user feedback</li>
     *   <li>Redirects to the login page on success</li>
     * </ol>
     * 
     * @return Navigation outcome string, or null to stay on the same page
     * 
     * @see #redirectIfLoggedIn()
     * @see LoginBean
     */
    public String register() {
        FacesContext context = FacesContext.getCurrentInstance();
        
        // Check for validation errors before proceeding
        if (context.isValidationFailed() || !context.getMessageList().isEmpty()) {
            System.out.println("Validation failed in register()");
            context.getMessageList().forEach(m -> 
                System.out.println("Validation error: " + m.getSummary() + " - " + m.getDetail())
            );
            return null;
        }

        try {
            // Prepare the API endpoint
            URL url = URI.create("http://localhost:9090/api/auth/register").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Configure the HTTP request
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Create the JSON payload with user registration data
            String payload = String.format(
                    "{\"fullName\":\"%s %s\", \"email\":\"%s\", \"password\":\"%s\", \"phoneNumber\":\"%s\"}",
                    escapeJson(firstName), 
                    escapeJson(lastName), 
                    escapeJson(email), 
                    escapeJson(password), 
                    escapeJson(phoneNumber)
            );

            // Send the request body
            System.out.println("Sending registration request to: " + url);
            System.out.println("Request payload: " + payload);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }

            // Process the response
            int statusCode = conn.getResponseCode();
            System.out.println("Response status code: " + statusCode);
            
            // Read response body for debugging
            try (java.util.Scanner scanner = new java.util.Scanner(conn.getErrorStream() == null ? conn.getInputStream() : conn.getErrorStream())) {
                String responseBody = scanner.useDelimiter("\\A").next();
                System.out.println("Response body: " + responseBody);
            } catch (Exception e) {
                System.out.println("Error reading response: " + e.getMessage());
            }

            if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_CREATED) {
                // Set up success message to be shown after redirect
                FacesContext ctx = FacesContext.getCurrentInstance();
                ctx.getExternalContext().getFlash().setKeepMessages(true);
                ctx.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_INFO,
                        "Registration successful. Please log in.",
                        null
                ));

                return "login.xhtml?faces-redirect=true";
            } else {
                // Handle non-successful response
                String errorMessage = "Registration failed. ";
                if (statusCode == HttpURLConnection.HTTP_CONFLICT) {
                    errorMessage += "Email already in use.";
                } else if (statusCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                    errorMessage += "Invalid registration data.";
                } else {
                    errorMessage += "Please try again later.";
                }
                
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, errorMessage, null));
                return null;
            }

        } catch (Exception e) {
            // Log the error and show a user-friendly message
            e.printStackTrace();
            String errorMessage = "An unexpected error occurred during registration. " +
                               "Please try again later.";
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                errorMessage = "Error: " + e.getMessage();
            }
            
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, errorMessage, null));
            return null;
        }
    }
    
    /**
     * Escapes special characters in a string to be safely used in JSON.
     * This is a basic implementation and might need enhancement for production use.
     * 
     * @param input The string to escape
     * @return The escaped string, or empty string if input is null
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Redirects the user to the home page if they are already logged in.
     * This method is typically called during page initialization to prevent
     * authenticated users from accessing the registration page.
     * 
     * @throws IOException if a redirect error occurs
     * 
     * @see LoginBean#isLoggedIn()
     */
    public void redirectIfLoggedIn() throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        
        // Get the login bean from the application context
        LoginBean loginBean = context.getApplication()
                .evaluateExpressionGet(context, "#{loginBean}", LoginBean.class);

        // Redirect to home if the user is already logged in
        if (loginBean != null && loginBean.isLoggedIn()) {
            String contextPath = context.getExternalContext().getRequestContextPath();
            context.getExternalContext().redirect(contextPath + "/home.xhtml");
        }
    }
}
