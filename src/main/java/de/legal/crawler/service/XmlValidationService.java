package de.legal.crawler.service;

import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.exception.XmlValidationException.ValidationError;
import de.legal.crawler.util.XmlSanitizer;
import de.legal.crawler.validator.EcliValidator;
import de.legal.crawler.validator.LegalDocMLValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive XML validation service for legal documents
 * 
 * Provides:
 * - XML schema validation
 * - LegalDocML.de standard compliance validation  
 * - ECLI identifier extraction and validation
 * - Security sanitization
 * - Enhanced error handling and reporting
 */
@Service
public class XmlValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(XmlValidationService.class);
    
    @Autowired
    private XmlSanitizer xmlSanitizer;
    
    @Autowired
    private EcliValidator ecliValidator;
    
    @Autowired
    private LegalDocMLValidator legalDocMLValidator;
    
    @Value("${validation.xml.schema-validation-enabled:true}")
    private boolean schemaValidationEnabled;
    
    @Value("${validation.xml.legaldocml-validation-enabled:true}")
    private boolean legalDocMLValidationEnabled;
    
    @Value("${validation.xml.ecli-validation-enabled:true}")
    private boolean ecliValidationEnabled;
    
    @Value("${validation.xml.strict-mode:false}")
    private boolean strictMode;
    
    /**
     * Perform comprehensive validation of XML legal document content
     * 
     * @param xmlContent The XML content to validate
     * @return ComprehensiveValidationResult with all validation results
     */
    public CompletableFuture<ComprehensiveValidationResult> validateLegalDocument(String xmlContent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting comprehensive XML validation");
                
                ComprehensiveValidationResult result = new ComprehensiveValidationResult();
                result.setOriginalSize(xmlContent != null ? xmlContent.length() : 0);
                
                // Step 1: Security sanitization (always performed)
                String sanitizedXml = performSanitization(xmlContent, result);
                result.setSanitizedSize(sanitizedXml.length());
                
                // Step 2: Basic XML structure validation
                validateXmlStructure(sanitizedXml, result);
                
                // Step 3: Schema validation (if enabled and schema available)
                if (schemaValidationEnabled) {
                    performSchemaValidation(sanitizedXml, result);
                }
                
                // Step 4: LegalDocML.de validation (if enabled)
                if (legalDocMLValidationEnabled) {
                    performLegalDocMLValidation(sanitizedXml, result);
                }
                
                // Step 5: ECLI extraction and validation (if enabled)
                if (ecliValidationEnabled) {
                    performEcliValidation(sanitizedXml, result);
                }
                
                // Step 6: Content analysis
                performContentAnalysis(sanitizedXml, result);
                
                // Determine overall validation status
                determineOverallValidation(result);
                
                logger.info("XML validation completed: {}", result.getSummary());
                
                return result;
                
            } catch (Exception e) {
                logger.error("Comprehensive validation failed: {}", e.getMessage());
                
                ComprehensiveValidationResult errorResult = new ComprehensiveValidationResult();
                errorResult.addError("Validation process failed: " + e.getMessage());
                errorResult.setValid(false);
                
                return errorResult;
            }
        });
    }
    
    /**
     * Quick validation check - performs minimal validation for performance
     * 
     * @param xmlContent The XML content to validate
     * @return ValidationSummary with essential validation information
     */
    public ValidationSummary quickValidate(String xmlContent) throws XmlValidationException {
        ValidationSummary summary = new ValidationSummary();
        
        try {
            // Security check
            String sanitized = xmlSanitizer.sanitizeXml(xmlContent);
            summary.setSanitizationPassed(true);
            
            // Basic structure check
            validateXmlStructure(sanitized, null);
            summary.setStructureValid(true);
            
            // Quick format detection
            summary.setLegalDocMLFormat(legalDocMLValidator.isLegalDocMLFormat(sanitized));
            
            // ECLI extraction
            Set<String> ecliIds = ecliValidator.extractEcliFromXml(sanitized);
            summary.setEcliCount(ecliIds.size());
            
            summary.setValid(true);
            
        } catch (XmlValidationException e) {
            summary.setValid(false);
            summary.setErrorMessage(e.getMessage());
            summary.setSanitizationPassed(false);
        }
        
        return summary;
    }
    
    /**
     * Validate XML against a specific XSD schema
     */
    public void validateAgainstSchema(String xmlContent, InputStream schemaStream) throws XmlValidationException {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            
            // Configure factory for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            
            Source schemaSource = new StreamSource(schemaStream);
            Schema schema = factory.newSchema(schemaSource);
            
            Validator validator = schema.newValidator();
            
            // Configure validator for security
            validator.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            validator.setFeature("http://xml.org/sax/features/external-general-entities", false);
            validator.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            
            Source xmlSource = new StreamSource(
                new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))
            );
            
            validator.validate(xmlSource);
            
            logger.debug("Schema validation successful");
            
        } catch (Exception e) {
            throw new XmlValidationException(
                "Schema validation failed: " + e.getMessage(),
                e,
                ValidationError.SCHEMA_VALIDATION_FAILED
            );
        }
    }
    
    private String performSanitization(String xmlContent, ComprehensiveValidationResult result) 
            throws XmlValidationException {
        try {
            String sanitized = xmlSanitizer.sanitizeXml(xmlContent);
            result.setSanitizationPassed(true);
            result.addValidation("Security", "XML sanitization completed successfully");
            return sanitized;
            
        } catch (XmlValidationException e) {
            result.setSanitizationPassed(false);
            result.addError("Sanitization failed: " + e.getMessage());
            
            if (strictMode) {
                throw e;
            }
            
            logger.warn("Sanitization failed but continuing in non-strict mode: {}", e.getMessage());
            return xmlContent; // Continue with original content in non-strict mode
        }
    }
    
    private void validateXmlStructure(String xmlContent, ComprehensiveValidationResult result) 
            throws XmlValidationException {
        try {
            // Use secure parser to validate basic XML structure
            xmlSanitizer.createSecureDocumentBuilderFactory()
                        .newDocumentBuilder()
                        .parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
            
            if (result != null) {
                result.setStructureValid(true);
                result.addValidation("Structure", "XML structure validation passed");
            }
            
        } catch (Exception e) {
            if (result != null) {
                result.setStructureValid(false);
                result.addError("XML structure validation failed: " + e.getMessage());
            }
            
            if (strictMode) {
                throw new XmlValidationException(
                    "XML structure validation failed: " + e.getMessage(),
                    e,
                    ValidationError.MALFORMED_XML
                );
            }
        }
    }
    
    private void performSchemaValidation(String xmlContent, ComprehensiveValidationResult result) {
        try {
            // Schema validation would be performed here if schemas are available
            // For now, we'll log that schema validation was requested
            logger.debug("Schema validation requested but no specific schema provided");
            result.addValidation("Schema", "Schema validation requested (no schema specified)");
            
        } catch (Exception e) {
            result.addWarning("Schema validation failed: " + e.getMessage());
        }
    }
    
    private void performLegalDocMLValidation(String xmlContent, ComprehensiveValidationResult result) {
        try {
            if (legalDocMLValidator.isLegalDocMLFormat(xmlContent)) {
                LegalDocMLValidator.LegalDocMLValidationResult legalDocMLResult = 
                    legalDocMLValidator.validateLegalDocML(xmlContent);
                
                result.setLegalDocMLFormat(true);
                result.setLegalDocMLValidationResult(legalDocMLResult);
                
                if (legalDocMLResult.isValid()) {
                    result.addValidation("LegalDocML", "LegalDocML.de validation passed");
                } else {
                    result.addWarning("LegalDocML.de validation issues found");
                    legalDocMLResult.getErrors().forEach(error -> result.addError("LegalDocML: " + error));
                    legalDocMLResult.getWarnings().forEach(warning -> result.addWarning("LegalDocML: " + warning));
                }
                
                // Extract document type
                try {
                    String docType = legalDocMLValidator.extractDocumentType(xmlContent);
                    result.setDocumentType(docType);
                } catch (Exception e) {
                    logger.debug("Could not extract document type: {}", e.getMessage());
                }
                
            } else {
                result.setLegalDocMLFormat(false);
                result.addValidation("LegalDocML", "Document is not in LegalDocML format");
            }
            
        } catch (Exception e) {
            result.addWarning("LegalDocML validation error: " + e.getMessage());
        }
    }
    
    private void performEcliValidation(String xmlContent, ComprehensiveValidationResult result) {
        try {
            Set<String> ecliIdentifiers = ecliValidator.extractEcliFromXml(xmlContent);
            result.setEcliIdentifiers(ecliIdentifiers);
            
            if (!ecliIdentifiers.isEmpty()) {
                result.addValidation("ECLI", "Found " + ecliIdentifiers.size() + " valid ECLI identifier(s)");
                
                // Validate each ECLI
                for (String ecli : ecliIdentifiers) {
                    try {
                        EcliValidator.ValidationResult ecliResult = ecliValidator.validateEcli(ecli);
                        if (ecliResult.getComponents().isGerman()) {
                            result.addValidation("ECLI", "German court ECLI found: " + ecli);
                        }
                    } catch (Exception e) {
                        result.addWarning("ECLI validation issue for " + ecli + ": " + e.getMessage());
                    }
                }
            } else {
                result.addValidation("ECLI", "No ECLI identifiers found in document");
            }
            
        } catch (Exception e) {
            result.addWarning("ECLI extraction error: " + e.getMessage());
        }
    }
    
    private void performContentAnalysis(String xmlContent, ComprehensiveValidationResult result) {
        try {
            // Analyze content characteristics
            int elementCount = xmlContent.split("<[^/!?]").length - 1;
            result.setElementCount(elementCount);
            
            boolean hasTextContent = xmlContent.replaceAll("<[^>]*>", "").trim().length() > 100;
            result.setHasSubstantialContent(hasTextContent);
            
            result.addValidation("Content", "Content analysis completed");
            
        } catch (Exception e) {
            result.addWarning("Content analysis failed: " + e.getMessage());
        }
    }
    
    private void determineOverallValidation(ComprehensiveValidationResult result) {
        boolean isValid = result.isSanitizationPassed() && result.isStructureValid();
        
        // In strict mode, all validations must pass
        if (strictMode) {
            isValid = isValid && result.getErrors().isEmpty();
        }
        
        // If LegalDocML validation was performed and failed, consider overall validation
        if (result.isLegalDocMLFormat() && result.getLegalDocMLValidationResult() != null) {
            if (strictMode && !result.getLegalDocMLValidationResult().isValid()) {
                isValid = false;
            }
        }
        
        result.setValid(isValid);
    }
    
    /**
     * Comprehensive validation result containing all validation information
     */
    public static class ComprehensiveValidationResult {
        private boolean valid = false;
        private boolean sanitizationPassed = false;
        private boolean structureValid = false;
        private boolean legalDocMLFormat = false;
        private String documentType;
        private int originalSize = 0;
        private int sanitizedSize = 0;
        private int elementCount = 0;
        private boolean hasSubstantialContent = false;
        private Set<String> ecliIdentifiers = Set.of();
        private LegalDocMLValidator.LegalDocMLValidationResult legalDocMLValidationResult;
        
        private final Set<String> validations = new java.util.HashSet<>();
        private final Set<String> warnings = new java.util.HashSet<>();
        private final Set<String> errors = new java.util.HashSet<>();
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public boolean isSanitizationPassed() { return sanitizationPassed; }
        public void setSanitizationPassed(boolean sanitizationPassed) { this.sanitizationPassed = sanitizationPassed; }
        
        public boolean isStructureValid() { return structureValid; }
        public void setStructureValid(boolean structureValid) { this.structureValid = structureValid; }
        
        public boolean isLegalDocMLFormat() { return legalDocMLFormat; }
        public void setLegalDocMLFormat(boolean legalDocMLFormat) { this.legalDocMLFormat = legalDocMLFormat; }
        
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        
        public int getOriginalSize() { return originalSize; }
        public void setOriginalSize(int originalSize) { this.originalSize = originalSize; }
        
        public int getSanitizedSize() { return sanitizedSize; }
        public void setSanitizedSize(int sanitizedSize) { this.sanitizedSize = sanitizedSize; }
        
        public int getElementCount() { return elementCount; }
        public void setElementCount(int elementCount) { this.elementCount = elementCount; }
        
        public boolean isHasSubstantialContent() { return hasSubstantialContent; }
        public void setHasSubstantialContent(boolean hasSubstantialContent) { this.hasSubstantialContent = hasSubstantialContent; }
        
        public Set<String> getEcliIdentifiers() { return ecliIdentifiers; }
        public void setEcliIdentifiers(Set<String> ecliIdentifiers) { this.ecliIdentifiers = ecliIdentifiers; }
        
        public LegalDocMLValidator.LegalDocMLValidationResult getLegalDocMLValidationResult() { 
            return legalDocMLValidationResult; 
        }
        public void setLegalDocMLValidationResult(LegalDocMLValidator.LegalDocMLValidationResult result) { 
            this.legalDocMLValidationResult = result; 
        }
        
        public void addValidation(String category, String message) {
            validations.add(category + ": " + message);
        }
        
        public void addWarning(String warning) { warnings.add(warning); }
        public void addError(String error) { errors.add(error); }
        
        public Set<String> getValidations() { return validations; }
        public Set<String> getWarnings() { return warnings; }
        public Set<String> getErrors() { return errors; }
        
        public String getSummary() {
            return String.format("Valid=%s, Sanitization=%s, Structure=%s, LegalDocML=%s, ECLIs=%d, Errors=%d, Warnings=%d",
                               valid, sanitizationPassed, structureValid, legalDocMLFormat, 
                               ecliIdentifiers.size(), errors.size(), warnings.size());
        }
    }
    
    /**
     * Quick validation summary for performance-critical scenarios
     */
    public static class ValidationSummary {
        private boolean valid = false;
        private boolean sanitizationPassed = false;
        private boolean structureValid = false;
        private boolean legalDocMLFormat = false;
        private int ecliCount = 0;
        private String errorMessage;
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public boolean isSanitizationPassed() { return sanitizationPassed; }
        public void setSanitizationPassed(boolean sanitizationPassed) { this.sanitizationPassed = sanitizationPassed; }
        
        public boolean isStructureValid() { return structureValid; }
        public void setStructureValid(boolean structureValid) { this.structureValid = structureValid; }
        
        public boolean isLegalDocMLFormat() { return legalDocMLFormat; }
        public void setLegalDocMLFormat(boolean legalDocMLFormat) { this.legalDocMLFormat = legalDocMLFormat; }
        
        public int getEcliCount() { return ecliCount; }
        public void setEcliCount(int ecliCount) { this.ecliCount = ecliCount; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}