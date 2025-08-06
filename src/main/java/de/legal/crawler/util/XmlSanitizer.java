package de.legal.crawler.util;

import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.exception.XmlValidationException.ValidationError;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * XML content sanitization and security validation utility
 * Provides protection against XML security vulnerabilities including:
 * - XXE (XML External Entity) attacks
 * - XML Bombs/Billion Laughs attacks
 * - DOCTYPE injection
 * - CDATA section abuse
 * - Invalid character sequences
 */
@Component
public class XmlSanitizer {
    
    private static final Logger logger = LoggerFactory.getLogger(XmlSanitizer.class);
    
    // Security patterns to detect potential threats
    private static final Pattern EXTERNAL_ENTITY_PATTERN = Pattern.compile(
        "<!ENTITY\\s+[^>]+\\s+(SYSTEM|PUBLIC)\\s+[^>]+>",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile(
        "<!DOCTYPE\\s+[^>]+>",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final Pattern XML_BOMB_PATTERN = Pattern.compile(
        "<!ENTITY\\s+\\w+\\s+[\"'][^\"']*(&\\w+;[^\"']*){10,}[\"']>",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    // Control characters that should not appear in legal documents
    private static final Pattern INVALID_CONTROL_CHARS = Pattern.compile(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"
    );
    
    // Maximum reasonable XML size (10MB)
    private static final int MAX_XML_SIZE = 10 * 1024 * 1024;
    
    // Maximum entity expansion ratio to prevent XML bombs
    private static final int MAX_ENTITY_EXPANSION_RATIO = 10;
    
    /**
     * Sanitize XML content and validate for security issues
     * 
     * @param xmlContent The XML content to sanitize
     * @return Sanitized XML content
     * @throws XmlValidationException if security violations are detected
     */
    public String sanitizeXml(String xmlContent) throws XmlValidationException {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            throw new XmlValidationException("XML content cannot be null or empty");
        }
        
        // Check size limits
        validateXmlSize(xmlContent);
        
        // Remove BOM if present
        xmlContent = removeBom(xmlContent);
        
        // Validate encoding
        validateEncoding(xmlContent);
        
        // Check for security threats
        validateSecurityThreats(xmlContent);
        
        // Remove invalid control characters
        xmlContent = removeInvalidControlCharacters(xmlContent);
        
        // Validate XML structure without resolving external entities
        validateXmlStructure(xmlContent);
        
        logger.debug("XML content sanitized successfully, size: {} bytes", xmlContent.length());
        
        return xmlContent;
    }
    
    /**
     * Create a secure XML factory configuration
     */
    public DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        
        try {
            // Disable external entity processing
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
            // Enable secure processing
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            
            // Disable expansion of entity references
            factory.setExpandEntityReferences(false);
            
            // Set namespace awareness for better parsing
            factory.setNamespaceAware(true);
            
            // Disable validation initially (will be done explicitly later)
            factory.setValidating(false);
            
        } catch (Exception e) {
            logger.warn("Could not configure all security features for DocumentBuilderFactory: {}", e.getMessage());
        }
        
        return factory;
    }
    
    /**
     * Create a secure SAX parser factory configuration
     */
    public SAXParserFactory createSecureSAXParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        
        try {
            // Disable external entity processing
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            
            // Enable secure processing
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            
            // Set namespace awareness
            factory.setNamespaceAware(true);
            
        } catch (Exception e) {
            logger.warn("Could not configure all security features for SAXParserFactory: {}", e.getMessage());
        }
        
        return factory;
    }
    
    /**
     * Create secure XML input factory for StAX parsing
     */
    public XMLInputFactory createSecureXMLInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        
        // Disable external entity processing
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        
        return factory;
    }
    
    private void validateXmlSize(String xmlContent) throws XmlValidationException {
        if (xmlContent.length() > MAX_XML_SIZE) {
            throw new XmlValidationException(
                "XML content exceeds maximum allowed size: " + xmlContent.length() + " bytes",
                ValidationError.SECURITY_VIOLATION
            );
        }
    }
    
    private String removeBom(String xmlContent) {
        // Remove UTF-8 BOM if present
        if (xmlContent.startsWith("\uFEFF")) {
            return xmlContent.substring(1);
        }
        return xmlContent;
    }
    
    private void validateEncoding(String xmlContent) throws XmlValidationException {
        // Check if content can be encoded/decoded properly
        try {
            byte[] bytes = xmlContent.getBytes(StandardCharsets.UTF_8);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            if (!xmlContent.equals(decoded)) {
                throw new XmlValidationException(
                    "Content encoding validation failed",
                    ValidationError.INVALID_ENCODING
                );
            }
        } catch (Exception e) {
            throw new XmlValidationException(
                "Invalid character encoding detected: " + e.getMessage(),
                e,
                ValidationError.INVALID_ENCODING
            );
        }
    }
    
    private void validateSecurityThreats(String xmlContent) throws XmlValidationException {
        // Check for external entity references
        if (EXTERNAL_ENTITY_PATTERN.matcher(xmlContent).find()) {
            throw new XmlValidationException(
                "External entity declaration detected",
                ValidationError.EXTERNAL_ENTITY_DETECTED
            );
        }
        
        // Check for DOCTYPE declarations
        if (DOCTYPE_PATTERN.matcher(xmlContent).find()) {
            throw new XmlValidationException(
                "DOCTYPE declaration detected",
                ValidationError.DOCTYPE_DECLARATION_DETECTED
            );
        }
        
        // Check for potential XML bombs
        if (XML_BOMB_PATTERN.matcher(xmlContent).find()) {
            throw new XmlValidationException(
                "Potential XML bomb attack pattern detected",
                ValidationError.XML_BOMB_DETECTED
            );
        }
        
        // Check entity expansion ratio
        validateEntityExpansionRatio(xmlContent);
    }
    
    private void validateEntityExpansionRatio(String xmlContent) throws XmlValidationException {
        int entityCount = (xmlContent.split("&\\w+;").length - 1);
        if (entityCount > 0) {
            int expansionRatio = xmlContent.length() / entityCount;
            if (expansionRatio > MAX_ENTITY_EXPANSION_RATIO) {
                throw new XmlValidationException(
                    "Suspicious entity expansion ratio detected: " + expansionRatio,
                    ValidationError.XML_BOMB_DETECTED
                );
            }
        }
    }
    
    private String removeInvalidControlCharacters(String xmlContent) {
        return INVALID_CONTROL_CHARS.matcher(xmlContent).replaceAll("");
    }
    
    private void validateXmlStructure(String xmlContent) throws XmlValidationException {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new XmlValidationException(
                "XML structure validation failed: " + e.getMessage(),
                e,
                ValidationError.MALFORMED_XML
            );
        }
    }
    
    /**
     * Sanitize text content for safe XML inclusion
     */
    public String sanitizeTextContent(String textContent) {
        if (textContent == null) {
            return null;
        }
        
        // Use OWASP encoder for XML content
        return Encode.forXmlContent(textContent);
    }
    
    /**
     * Sanitize attribute values for safe XML inclusion
     */
    public String sanitizeAttributeValue(String attributeValue) {
        if (attributeValue == null) {
            return null;
        }
        
        // Use OWASP encoder for XML attributes
        return Encode.forXmlAttribute(attributeValue);
    }
}