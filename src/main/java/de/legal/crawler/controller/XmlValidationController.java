package de.legal.crawler.controller;

import de.legal.crawler.service.XmlValidationService;
import de.legal.crawler.service.XmlValidationService.ComprehensiveValidationResult;
import de.legal.crawler.service.XmlValidationService.ValidationSummary;
import de.legal.crawler.exception.XmlValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for XML validation endpoints
 * Provides API access to XML validation functionality
 */
@RestController
@RequestMapping("/api/xml-validation")
@CrossOrigin(origins = "*")
public class XmlValidationController {
    
    private static final Logger logger = LoggerFactory.getLogger(XmlValidationController.class);
    
    @Autowired
    private XmlValidationService xmlValidationService;
    
    /**
     * Perform comprehensive XML validation
     * 
     * @param request The validation request containing XML content
     * @return Comprehensive validation result
     */
    @PostMapping(value = "/validate", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResponse> validateXml(@RequestBody ValidationRequest request) {
        try {
            logger.info("Received XML validation request");
            
            if (request.getXmlContent() == null || request.getXmlContent().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ValidationResponse(false, "XML content cannot be null or empty", null));
            }
            
            CompletableFuture<ComprehensiveValidationResult> future = 
                xmlValidationService.validateLegalDocument(request.getXmlContent());
            
            // Wait for validation with timeout
            ComprehensiveValidationResult result = future.get(30, TimeUnit.SECONDS);
            
            ValidationResponse response = new ValidationResponse(true, "Validation completed", result);
            
            logger.info("XML validation completed: {}", result.getSummary());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("XML validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ValidationResponse(false, "Validation failed: " + e.getMessage(), null));
        }
    }
    
    /**
     * Perform quick XML validation for performance-critical scenarios
     * 
     * @param request The validation request containing XML content
     * @return Quick validation summary
     */
    @PostMapping(value = "/validate/quick",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QuickValidationResponse> quickValidateXml(@RequestBody ValidationRequest request) {
        try {
            logger.info("Received quick XML validation request");
            
            if (request.getXmlContent() == null || request.getXmlContent().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new QuickValidationResponse(false, "XML content cannot be null or empty", null));
            }
            
            ValidationSummary summary = xmlValidationService.quickValidate(request.getXmlContent());
            
            QuickValidationResponse response = new QuickValidationResponse(true, "Quick validation completed", summary);
            
            logger.info("Quick XML validation completed: valid={}", summary.isValid());
            
            return ResponseEntity.ok(response);
            
        } catch (XmlValidationException e) {
            logger.warn("Quick XML validation failed: {}", e.getMessage());
            ValidationSummary errorSummary = new ValidationSummary();
            errorSummary.setValid(false);
            errorSummary.setErrorMessage(e.getMessage());
            
            return ResponseEntity.ok(new QuickValidationResponse(true, "Validation completed with errors", errorSummary));
            
        } catch (Exception e) {
            logger.error("Quick XML validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new QuickValidationResponse(false, "Validation error: " + e.getMessage(), null));
        }
    }
    
    /**
     * Validate XML content directly from multipart form data (for file uploads)
     * 
     * @param xmlContent Raw XML content
     * @return Validation result
     */
    @PostMapping(value = "/validate/content",
                 consumes = MediaType.TEXT_XML_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResponse> validateXmlContent(@RequestBody String xmlContent) {
        try {
            logger.info("Received direct XML content validation request");
            
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ValidationResponse(false, "XML content cannot be null or empty", null));
            }
            
            CompletableFuture<ComprehensiveValidationResult> future = 
                xmlValidationService.validateLegalDocument(xmlContent);
            
            ComprehensiveValidationResult result = future.get(30, TimeUnit.SECONDS);
            
            ValidationResponse response = new ValidationResponse(true, "Validation completed", result);
            
            logger.info("Direct XML validation completed: {}", result.getSummary());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Direct XML validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ValidationResponse(false, "Validation failed: " + e.getMessage(), null));
        }
    }
    
    /**
     * Get validation statistics and system health
     * 
     * @return System validation statistics
     */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationStatsResponse> getValidationStats() {
        try {
            ValidationStatsResponse stats = new ValidationStatsResponse();
            stats.setSuccess(true);
            stats.setMessage("Validation system operational");
            
            // Add basic system info
            stats.setValidationSystemEnabled(true);
            stats.setSchemaValidationAvailable(true);
            stats.setLegalDocMLValidationAvailable(true);
            stats.setEcliValidationAvailable(true);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve validation stats: {}", e.getMessage());
            ValidationStatsResponse errorStats = new ValidationStatsResponse();
            errorStats.setSuccess(false);
            errorStats.setMessage("Failed to retrieve stats: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStats);
        }
    }
    
    // Request/Response DTOs
    
    public static class ValidationRequest {
        private String xmlContent;
        private boolean strictMode = false;
        private boolean includeWarnings = true;
        
        // Constructors
        public ValidationRequest() {}
        
        public ValidationRequest(String xmlContent) {
            this.xmlContent = xmlContent;
        }
        
        // Getters and setters
        public String getXmlContent() { return xmlContent; }
        public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }
        
        public boolean isStrictMode() { return strictMode; }
        public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }
        
        public boolean isIncludeWarnings() { return includeWarnings; }
        public void setIncludeWarnings(boolean includeWarnings) { this.includeWarnings = includeWarnings; }
    }
    
    public static class ValidationResponse {
        private boolean success;
        private String message;
        private ComprehensiveValidationResult validationResult;
        private long timestamp;
        
        public ValidationResponse() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public ValidationResponse(boolean success, String message, ComprehensiveValidationResult validationResult) {
            this();
            this.success = success;
            this.message = message;
            this.validationResult = validationResult;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public ComprehensiveValidationResult getValidationResult() { return validationResult; }
        public void setValidationResult(ComprehensiveValidationResult validationResult) { 
            this.validationResult = validationResult; 
        }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    public static class QuickValidationResponse {
        private boolean success;
        private String message;
        private ValidationSummary validationSummary;
        private long timestamp;
        
        public QuickValidationResponse() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public QuickValidationResponse(boolean success, String message, ValidationSummary validationSummary) {
            this();
            this.success = success;
            this.message = message;
            this.validationSummary = validationSummary;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public ValidationSummary getValidationSummary() { return validationSummary; }
        public void setValidationSummary(ValidationSummary validationSummary) { 
            this.validationSummary = validationSummary; 
        }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    public static class ValidationStatsResponse {
        private boolean success;
        private String message;
        private boolean validationSystemEnabled;
        private boolean schemaValidationAvailable;
        private boolean legalDocMLValidationAvailable;
        private boolean ecliValidationAvailable;
        private long timestamp;
        
        public ValidationStatsResponse() {
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public boolean isValidationSystemEnabled() { return validationSystemEnabled; }
        public void setValidationSystemEnabled(boolean validationSystemEnabled) { 
            this.validationSystemEnabled = validationSystemEnabled; 
        }
        
        public boolean isSchemaValidationAvailable() { return schemaValidationAvailable; }
        public void setSchemaValidationAvailable(boolean schemaValidationAvailable) { 
            this.schemaValidationAvailable = schemaValidationAvailable; 
        }
        
        public boolean isLegalDocMLValidationAvailable() { return legalDocMLValidationAvailable; }
        public void setLegalDocMLValidationAvailable(boolean legalDocMLValidationAvailable) { 
            this.legalDocMLValidationAvailable = legalDocMLValidationAvailable; 
        }
        
        public boolean isEcliValidationAvailable() { return ecliValidationAvailable; }
        public void setEcliValidationAvailable(boolean ecliValidationAvailable) { 
            this.ecliValidationAvailable = ecliValidationAvailable; 
        }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}