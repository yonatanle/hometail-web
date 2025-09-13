package com.hometail.web.validator;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.validator.FacesValidator;
import jakarta.faces.validator.Validator;
import jakarta.faces.validator.ValidatorException;

/**
 * Custom JSF validator that ensures the password and confirm password fields match.
 * This validator is used in registration and password change forms to verify that
 * the user has entered the same password in both the password and confirm password fields.
 * 
 * <p><b>Usage in JSF:</b></p>
 * <pre>
 * &lt;p:password id="password" value="#{bean.password}" ... /&gt;
 * &lt;p:password id="confirmPassword" ...&gt;
 *     &lt;f:validator validatorId="passwordMatchValidator" /&gt;
 *     &lt;f:attribute name="passwordId" value=":form:password" /&gt;
 * &lt;/p:password&gt;
 * </pre>
 * 
 * @see jakarta.faces.validator.Validator
 * @see jakarta.faces.validator.FacesValidator
 */
@FacesValidator("passwordMatchValidator")
public class PasswordMatchValidator implements Validator {

    /**
     * Validates that the confirm password matches the original password.
     * 
     * @param context   The FacesContext for the current request
     * @param component The UIComponent being validated (confirm password field)
     * @param value     The value of the confirm password field
     * @throws ValidatorException if validation fails
     * 
     * @implNote This method:
     * <ol>
     *   <li>Gets the password field ID from the component's attributes</li>
     *   <li>Finds the password component using the ID</li>
     *   <li>Retrieves the password value from the component</li>
     *   <li>Compares the password and confirm password values</li>
     *   <li>Throws a ValidatorException if they don't match</li>
     * </ol>
     */
    @Override
    public void validate(FacesContext context, UIComponent component, Object value) 
            throws ValidatorException {
        // The value parameter contains the confirm password value
        String confirmPassword = (String) value;
        
        // Get the password field ID from the component's attributes
        String passwordId = (String) component.getAttributes().get("passwordId");
        if (passwordId == null || passwordId.trim().isEmpty()) {
            throw new ValidatorException(
                new FacesMessage("Password field reference is not configured"));
        }
        
        // Find the password component in the component tree
        UIComponent passwordComponent = component.findComponent(passwordId);
        if (passwordComponent == null) {
            throw new ValidatorException(
                new FacesMessage("Password field not found. Check the passwordId attribute."));
        }
        
        // Get the password value from the password component
        String password = (String) passwordComponent.getAttributes().get("value");
        
        // Check if passwords match (both null or equal strings)
        if (confirmPassword == null || !confirmPassword.equals(password)) {
            throw new ValidatorException(
                new FacesMessage(FacesMessage.SEVERITY_ERROR, 
                               "Passwords do not match. Please ensure both entries are identical.",
                               null));
        }
    }
}
