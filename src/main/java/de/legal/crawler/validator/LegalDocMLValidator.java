package de.legal.crawler.validator;

import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.exception.XmlValidationException.ValidationError;
import de.legal.crawler.util.XmlSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Validator for LegalDocML.de standard compliance
 * 
 * LegalDocML.de is the German application profile of the international 
 * OASIS LegalDocML (Akoma Ntoso) standard for legal document markup.
 * 
 * This validator checks for:
 * - Proper namespace declarations
 * - Required root elements
 * - Mandatory metadata elements
 * - Document structure compliance
 * - Identifier validation (eId, wId, GUID)
 * - German-specific legal document patterns
 */
@Component
public class LegalDocMLValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(LegalDocMLValidator.class);
    
    @Autowired
    private XmlSanitizer xmlSanitizer;
    
    // LegalDocML namespace
    private static final String LEGALDOCML_NAMESPACE = "http://docs.oasis-open.org/legaldocml/ns/akn/3.0";
    private static final String LEGALDOCML_DE_NAMESPACE = "http://www.legaldocml.de/1.0/";
    
    // Required root elements for different document types
    private static final Set<String> VALID_ROOT_ELEMENTS = Set.of(
        "akomaNtoso",   // Standard Akoma Ntoso root
        "act",          // Legislative act
        "bill",         // Draft bill
        "doc",          // Generic document
        "judgment",     // Court judgment
        "portion",      // Document portion
        "documentCollection" // Collection of documents
    );
    
    // Required metadata elements
    private static final Set<String> REQUIRED_METADATA_ELEMENTS = Set.of(
        "identification",
        "publication",
        "lifecycle"
    );
    
    // German court document specific elements
    private static final Set<String> GERMAN_COURT_ELEMENTS = Set.of(
        "courtType",
        "docketNumber", 
        "decisionDate",
        "judges",
        "procedure"
    );
    
    // Document structure elements that should be present
    private static final Set<String> STRUCTURE_ELEMENTS = Set.of(
        "meta",
        "preface",
        "preamble", 
        "body",
        "conclusions"
    );
    
    /**
     * Validate XML content against LegalDocML.de standard
     * 
     * @param xmlContent The XML content to validate
     * @return ValidationResult with detailed validation information
     * @throws XmlValidationException if validation fails
     */
    public LegalDocMLValidationResult validateLegalDocML(String xmlContent) throws XmlValidationException {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            throw new XmlValidationException(
                "XML content cannot be null or empty",
                ValidationError.LEGALDOCML_VALIDATION_FAILED
            );
        }
        
        // First sanitize the XML content
        String sanitizedXml = xmlSanitizer.sanitizeXml(xmlContent);
        
        // Parse the document
        Document document = parseDocument(sanitizedXml);
        
        // Perform validation checks
        LegalDocMLValidationResult result = new LegalDocMLValidationResult();
        
        validateNamespaces(document, result);
        validateRootElement(document, result);
        validateMetadata(document, result);
        validateStructure(document, result);
        validateIdentifiers(document, result);
        validateGermanSpecificElements(document, result);
        
        // Determine overall validation status
        result.setValid(!result.hasErrors());
        
        if (result.isValid()) {
            logger.info("LegalDocML validation successful");
        } else {
            logger.warn("LegalDocML validation failed: {}", result.getErrors());
        }
        
        return result;
    }
    
    /**
     * Check if XML content appears to be LegalDocML format
     */
    public boolean isLegalDocMLFormat(String xmlContent) {
        try {
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                return false;
            }
            
            // Quick check for LegalDocML indicators
            return xmlContent.contains(LEGALDOCML_NAMESPACE) ||
                   xmlContent.contains(LEGALDOCML_DE_NAMESPACE) ||
                   xmlContent.contains("akomaNtoso") ||
                   xmlContent.contains("akn:");
                   
        } catch (Exception e) {
            logger.debug("Error checking LegalDocML format: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract document type from LegalDocML content
     */
    public String extractDocumentType(String xmlContent) throws XmlValidationException {
        Document document = parseDocument(xmlContent);
        Element root = document.getDocumentElement();
        
        // Check for document type in various locations
        String docType = extractFromAttribute(root, "name");
        if (docType == null) {
            docType = extractFromMetadata(document, "type");
        }
        if (docType == null) {
            docType = root.getLocalName(); // Fall back to root element name
        }
        
        return docType;
    }
    
    private Document parseDocument(String xmlContent) throws XmlValidationException {
        try {
            DocumentBuilderFactory factory = xmlSanitizer.createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
            
        } catch (Exception e) {
            throw new XmlValidationException(
                "Failed to parse LegalDocML document: " + e.getMessage(),
                e,
                ValidationError.MALFORMED_XML
            );
        }
    }
    
    private void validateNamespaces(Document document, LegalDocMLValidationResult result) {
        Element root = document.getDocumentElement();
        
        // Check for LegalDocML namespace
        boolean hasLegalDocMLNamespace = false;
        String namespaceURI = root.getNamespaceURI();
        
        if (LEGALDOCML_NAMESPACE.equals(namespaceURI) || 
            LEGALDOCML_DE_NAMESPACE.equals(namespaceURI)) {
            hasLegalDocMLNamespace = true;
            result.addValidation("Namespace validation", "Valid LegalDocML namespace found");
        }
        
        // Check namespace declarations
        if (root.hasAttributes()) {
            for (int i = 0; i < root.getAttributes().getLength(); i++) {
                String attrName = root.getAttributes().item(i).getNodeName();
                String attrValue = root.getAttributes().item(i).getNodeValue();
                
                if (attrName.startsWith("xmlns") && 
                    (LEGALDOCML_NAMESPACE.equals(attrValue) || LEGALDOCML_DE_NAMESPACE.equals(attrValue))) {
                    hasLegalDocMLNamespace = true;
                    break;
                }
            }
        }
        
        if (!hasLegalDocMLNamespace) {
            result.addError("Missing LegalDocML namespace declaration");
        }
    }
    
    private void validateRootElement(Document document, LegalDocMLValidationResult result) {
        Element root = document.getDocumentElement();
        String rootElementName = root.getLocalName();
        
        if (VALID_ROOT_ELEMENTS.contains(rootElementName)) {
            result.addValidation("Root element", "Valid root element: " + rootElementName);
        } else {
            result.addWarning("Unexpected root element: " + rootElementName);
        }
    }
    
    private void validateMetadata(Document document, LegalDocMLValidationResult result) {
        // Look for meta element
        NodeList metaElements = document.getElementsByTagNameNS("*", "meta");
        if (metaElements.getLength() == 0) {
            result.addError("Missing required 'meta' element");
            return;
        }
        
        Element metaElement = (Element) metaElements.item(0);
        
        // Check for required metadata subelements
        for (String requiredElement : REQUIRED_METADATA_ELEMENTS) {
            NodeList elements = metaElement.getElementsByTagNameNS("*", requiredElement);
            if (elements.getLength() == 0) {
                result.addWarning("Missing recommended metadata element: " + requiredElement);
            } else {
                result.addValidation("Metadata", "Found required element: " + requiredElement);
            }
        }
        
        // Validate identification element specifically
        validateIdentificationElement(metaElement, result);
    }
    
    private void validateIdentificationElement(Element metaElement, LegalDocMLValidationResult result) {
        NodeList identificationElements = metaElement.getElementsByTagNameNS("*", "identification");
        if (identificationElements.getLength() > 0) {
            Element identification = (Element) identificationElements.item(0);
            
            // Check for FRBRWork, FRBRExpression, FRBRManifestation
            String[] frbrLevels = {"FRBRWork", "FRBRExpression", "FRBRManifestation"};
            for (String level : frbrLevels) {
                NodeList levelElements = identification.getElementsByTagNameNS("*", level);
                if (levelElements.getLength() > 0) {
                    result.addValidation("FRBR Model", "Found " + level + " element");
                } else {
                    result.addWarning("Missing FRBR element: " + level);
                }
            }
        }
    }
    
    private void validateStructure(Document document, LegalDocMLValidationResult result) {
        // Check for main structural elements
        for (String structureElement : STRUCTURE_ELEMENTS) {
            NodeList elements = document.getElementsByTagNameNS("*", structureElement);
            if (elements.getLength() > 0) {
                result.addValidation("Structure", "Found structural element: " + structureElement);
            }
        }
        
        // Body element is typically required
        NodeList bodyElements = document.getElementsByTagNameNS("*", "body");
        if (bodyElements.getLength() == 0) {
            result.addWarning("Missing 'body' element - document may be incomplete");
        }
    }
    
    private void validateIdentifiers(Document document, LegalDocMLValidationResult result) {
        validateEIdPattern(document, result);
        validateWIdPattern(document, result);
        validateGuidPattern(document, result);
    }
    
    private void validateEIdPattern(Document document, LegalDocMLValidationResult result) {
        // Elements with eId attributes
        NodeList allElements = document.getElementsByTagName("*");
        int eIdCount = 0;
        
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (element.hasAttribute("eId")) {
                eIdCount++;
                String eId = element.getAttribute("eId");
                if (!isValidEId(eId)) {
                    result.addWarning("Invalid eId format: " + eId);
                }
            }
        }
        
        if (eIdCount > 0) {
            result.addValidation("Identifiers", "Found " + eIdCount + " eId attributes");
        }
    }
    
    private void validateWIdPattern(Document document, LegalDocMLValidationResult result) {
        NodeList allElements = document.getElementsByTagName("*");
        int wIdCount = 0;
        
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (element.hasAttribute("wId")) {
                wIdCount++;
                String wId = element.getAttribute("wId");
                if (!isValidWId(wId)) {
                    result.addWarning("Invalid wId format: " + wId);
                }
            }
        }
        
        if (wIdCount > 0) {
            result.addValidation("Identifiers", "Found " + wIdCount + " wId attributes");
        }
    }
    
    private void validateGuidPattern(Document document, LegalDocMLValidationResult result) {
        NodeList allElements = document.getElementsByTagName("*");
        int guidCount = 0;
        
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (element.hasAttribute("GUID")) {
                guidCount++;
                String guid = element.getAttribute("GUID");
                if (!isValidGuid(guid)) {
                    result.addWarning("Invalid GUID format: " + guid);
                }
            }
        }
        
        if (guidCount > 0) {
            result.addValidation("Identifiers", "Found " + guidCount + " GUID attributes");
        }
    }
    
    private void validateGermanSpecificElements(Document document, LegalDocMLValidationResult result) {
        // Check for German court-specific elements if this appears to be a judgment
        if (isJudgmentDocument(document)) {
            for (String germanElement : GERMAN_COURT_ELEMENTS) {
                NodeList elements = document.getElementsByTagNameNS("*", germanElement);
                if (elements.getLength() > 0) {
                    result.addValidation("German Elements", "Found German court element: " + germanElement);
                }
            }
        }
    }
    
    private boolean isJudgmentDocument(Document document) {
        Element root = document.getDocumentElement();
        return "judgment".equals(root.getLocalName()) ||
               document.getElementsByTagNameNS("*", "judgment").getLength() > 0;
    }
    
    private boolean isValidEId(String eId) {
        // eId should follow hierarchical dot notation: part.chapter.section
        return eId != null && eId.matches("^[a-zA-Z0-9._-]+$") && !eId.startsWith(".") && !eId.endsWith(".");
    }
    
    private boolean isValidWId(String wId) {
        // wId should be a stable work identifier
        return wId != null && wId.matches("^[a-zA-Z0-9._-]+$") && wId.length() > 0;
    }
    
    private boolean isValidGuid(String guid) {
        // GUID should be a valid UUID format
        return guid != null && guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }
    
    private String extractFromAttribute(Element element, String attributeName) {
        return element.hasAttribute(attributeName) ? element.getAttribute(attributeName) : null;
    }
    
    private String extractFromMetadata(Document document, String elementName) {
        NodeList elements = document.getElementsByTagNameNS("*", elementName);
        return elements.getLength() > 0 ? elements.item(0).getTextContent().trim() : null;
    }
    
    /**
     * LegalDocML validation result container
     */
    public static class LegalDocMLValidationResult {
        private boolean valid = false;
        private final Set<String> errors = new HashSet<>();
        private final Set<String> warnings = new HashSet<>();
        private final Set<String> validations = new HashSet<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addValidation(String category, String message) {
            validations.add(category + ": " + message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public Set<String> getErrors() {
            return new HashSet<>(errors);
        }
        
        public Set<String> getWarnings() {
            return new HashSet<>(warnings);
        }
        
        public Set<String> getValidations() {
            return new HashSet<>(validations);
        }
        
        public String getSummary() {
            return String.format("LegalDocML Validation Result: Valid=%s, Errors=%d, Warnings=%d, Validations=%d",
                                valid, errors.size(), warnings.size(), validations.size());
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("LegalDocML Validation Result:\n");
            sb.append("Valid: ").append(valid).append("\n");
            
            if (!validations.isEmpty()) {
                sb.append("Validations:\n");
                validations.forEach(v -> sb.append("  ✓ ").append(v).append("\n"));
            }
            
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                warnings.forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
            }
            
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                errors.forEach(e -> sb.append("  ✗ ").append(e).append("\n"));
            }
            
            return sb.toString();
        }
    }
}