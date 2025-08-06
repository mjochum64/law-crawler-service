package de.legal.crawler.util;

import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.exception.XmlValidationException.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XML sanitizer
 */
class XmlSanitizerTest {

    private XmlSanitizer xmlSanitizer;

    @BeforeEach
    void setUp() {
        xmlSanitizer = new XmlSanitizer();
    }

    @Test
    void testSanitizeValidXml() throws XmlValidationException {
        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Test Document</title>
                <content>This is valid content</content>
            </document>
            """;
        
        String result = xmlSanitizer.sanitizeXml(validXml);
        assertNotNull(result);
        assertTrue(result.contains("<document>"));
        assertTrue(result.contains("Test Document"));
    }

    @Test
    void testDetectExternalEntity() {
        String maliciousXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE document [
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <document>&xxe;</document>
            """;
        
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml(maliciousXml);
        });
        
        assertEquals(ValidationError.EXTERNAL_ENTITY_DETECTED, exception.getValidationError());
        assertTrue(exception.getMessage().contains("External entity"));
    }

    @Test
    void testDetectDoctypeDeclaration() {
        String xmlWithDoctype = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE document>
            <document>
                <content>Test</content>
            </document>
            """;
        
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml(xmlWithDoctype);
        });
        
        assertEquals(ValidationError.DOCTYPE_DECLARATION_DETECTED, exception.getValidationError());
    }

    @Test
    void testDetectXmlBomb() {
        String xmlBomb = """
            <?xml version="1.0"?>
            <!DOCTYPE document [
                <!ENTITY lol "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
            ]>
            <document>&lol;</document>
            """;
        
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml(xmlBomb);
        });
        
        assertEquals(ValidationError.DOCTYPE_DECLARATION_DETECTED, exception.getValidationError());
    }

    @Test
    void testRemoveInvalidControlCharacters() throws XmlValidationException {
        String xmlWithControlChars = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <content>Text with\u0000control\u0001chars\u0008</content>
            </document>
            """;
        
        String result = xmlSanitizer.sanitizeXml(xmlWithControlChars);
        assertNotNull(result);
        assertFalse(result.contains("\u0000"));
        assertFalse(result.contains("\u0001"));
        assertFalse(result.contains("\u0008"));
    }

    @Test
    void testRemoveBom() throws XmlValidationException {
        String xmlWithBom = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?><document><content>Test</content></document>";
        
        String result = xmlSanitizer.sanitizeXml(xmlWithBom);
        assertNotNull(result);
        assertFalse(result.startsWith("\uFEFF"));
        assertTrue(result.startsWith("<?xml"));
    }

    @Test
    void testNullXmlContent() {
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml(null);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testEmptyXmlContent() {
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml("");
        });
        
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void testXmlSizeLimit() {
        // Create XML content larger than the limit (assuming 10MB limit)
        StringBuilder largeXml = new StringBuilder("<?xml version=\"1.0\"?><document>");
        // Add enough content to exceed size limit
        String largeContent = "x".repeat(1024 * 1024); // 1MB chunk
        for (int i = 0; i < 11; i++) { // 11MB total
            largeXml.append("<content>").append(largeContent).append("</content>");
        }
        largeXml.append("</document>");
        
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml(largeXml.toString());
        });
        
        assertEquals(ValidationError.SECURITY_VIOLATION, exception.getValidationError());
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
    }

    @Test
    void testMalformedXml() {
        String malformedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <unclosed-tag>
                <content>Missing closing tags
            </document>
            """;
        
        XmlValidationException exception = assertThrows(XmlValidationException.class, () -> {
            xmlSanitizer.sanitizeXml(malformedXml);
        });
        
        assertEquals(ValidationError.MALFORMED_XML, exception.getValidationError());
    }

    @Test
    void testSanitizeTextContent() {
        String textWithSpecialChars = "Text with <script>alert('xss')</script> & \"quotes\"";
        String sanitized = xmlSanitizer.sanitizeTextContent(textWithSpecialChars);
        
        assertNotNull(sanitized);
        assertFalse(sanitized.contains("<script>"));
        assertTrue(sanitized.contains("&lt;"));
        assertTrue(sanitized.contains("&amp;"));
    }

    @Test
    void testSanitizeAttributeValue() {
        String attributeValue = "value with \"quotes\" & <tags>";
        String sanitized = xmlSanitizer.sanitizeAttributeValue(attributeValue);
        
        assertNotNull(sanitized);
        assertFalse(sanitized.contains("\""));
        assertTrue(sanitized.contains("&quot;"));
        assertTrue(sanitized.contains("&amp;"));
        assertTrue(sanitized.contains("&lt;"));
    }

    @Test
    void testSanitizeNullTextContent() {
        String result = xmlSanitizer.sanitizeTextContent(null);
        assertNull(result);
    }

    @Test
    void testSanitizeNullAttributeValue() {
        String result = xmlSanitizer.sanitizeAttributeValue(null);
        assertNull(result);
    }

    @Test
    void testCreateSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = xmlSanitizer.createSecureDocumentBuilderFactory();
        
        assertNotNull(factory);
        assertTrue(factory.isNamespaceAware());
        assertFalse(factory.isExpandEntityReferences());
        assertFalse(factory.isValidating());
    }

    @Test
    void testCreateSecureSAXParserFactory() {
        var factory = xmlSanitizer.createSecureSAXParserFactory();
        
        assertNotNull(factory);
        assertTrue(factory.isNamespaceAware());
    }

    @Test
    void testCreateSecureXMLInputFactory() {
        var factory = xmlSanitizer.createSecureXMLInputFactory();
        
        assertNotNull(factory);
        assertEquals(Boolean.FALSE, factory.getProperty("javax.xml.stream.isSupportingExternalEntities"));
        assertEquals(Boolean.FALSE, factory.getProperty("javax.xml.stream.supportDTD"));
    }

    @Test
    void testValidXmlWithNamespaces() throws XmlValidationException {
        String xmlWithNamespaces = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document xmlns="http://example.com/ns" xmlns:legal="http://legal.example.com">
                <legal:case id="123">
                    <legal:title>Test Case</legal:title>
                </legal:case>
            </document>
            """;
        
        String result = xmlSanitizer.sanitizeXml(xmlWithNamespaces);
        assertNotNull(result);
        assertTrue(result.contains("xmlns"));
        assertTrue(result.contains("legal:case"));
    }

    @Test
    void testValidXmlWithCData() throws XmlValidationException {
        String xmlWithCData = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <content><![CDATA[This is CDATA content with <tags> and & symbols]]></content>
            </document>
            """;
        
        String result = xmlSanitizer.sanitizeXml(xmlWithCData);
        assertNotNull(result);
        assertTrue(result.contains("CDATA"));
    }

    @Test
    void testValidateEncodingWithValidUtf8() throws XmlValidationException {
        String validUtf8Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <content>Valid UTF-8 content with umlauts: äöü and emojis: 🚀</content>
            </document>
            """;
        
        String result = xmlSanitizer.sanitizeXml(validUtf8Xml);
        assertNotNull(result);
        assertTrue(result.contains("äöü"));
        assertTrue(result.contains("🚀"));
    }
}