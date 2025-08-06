package de.legal.crawler.exception;

/**
 * Exception thrown when XML validation fails
 */
public class XmlValidationException extends Exception {
    
    private final ValidationError validationError;
    
    public XmlValidationException(String message) {
        super(message);
        this.validationError = ValidationError.GENERIC_VALIDATION_ERROR;
    }
    
    public XmlValidationException(String message, ValidationError validationError) {
        super(message);
        this.validationError = validationError;
    }
    
    public XmlValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationError = ValidationError.GENERIC_VALIDATION_ERROR;
    }
    
    public XmlValidationException(String message, Throwable cause, ValidationError validationError) {
        super(message, cause);
        this.validationError = validationError;
    }
    
    public ValidationError getValidationError() {
        return validationError;
    }
    
    public enum ValidationError {
        GENERIC_VALIDATION_ERROR("Generic validation error"),
        SCHEMA_VALIDATION_FAILED("Schema validation failed"),
        MALFORMED_XML("Malformed XML document"),
        SECURITY_VIOLATION("Security violation detected"),
        LEGALDOCML_VALIDATION_FAILED("LegalDocML.de validation failed"),
        ECLI_VALIDATION_FAILED("ECLI identifier validation failed"),
        EXTERNAL_ENTITY_DETECTED("External entity detected"),
        DOCTYPE_DECLARATION_DETECTED("DOCTYPE declaration detected"),
        XML_BOMB_DETECTED("XML bomb/billion laughs attack detected"),
        INVALID_ENCODING("Invalid or dangerous encoding detected"),
        NAMESPACE_VIOLATION("Namespace validation failed");
        
        private final String description;
        
        ValidationError(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}