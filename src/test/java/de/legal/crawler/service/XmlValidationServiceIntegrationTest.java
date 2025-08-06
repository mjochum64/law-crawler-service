package de.legal.crawler.service;

import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.util.XmlSanitizer;
import de.legal.crawler.validator.EcliValidator;
import de.legal.crawler.validator.LegalDocMLValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for XML validation service
 */
class XmlValidationServiceIntegrationTest {

    private XmlValidationService xmlValidationService;
    private XmlSanitizer xmlSanitizer;
    private EcliValidator ecliValidator;
    private LegalDocMLValidator legalDocMLValidator;

    @BeforeEach
    void setUp() {
        xmlSanitizer = new XmlSanitizer();
        ecliValidator = new EcliValidator();
        legalDocMLValidator = new LegalDocMLValidator();
        
        xmlValidationService = new XmlValidationService();
        
        // Inject dependencies using reflection for testing
        ReflectionTestUtils.setField(xmlValidationService, "xmlSanitizer", xmlSanitizer);
        ReflectionTestUtils.setField(xmlValidationService, "ecliValidator", ecliValidator);
        ReflectionTestUtils.setField(xmlValidationService, "legalDocMLValidator", legalDocMLValidator);
        
        // Set default configuration values
        ReflectionTestUtils.setField(xmlValidationService, "schemaValidationEnabled", true);
        ReflectionTestUtils.setField(xmlValidationService, "legalDocMLValidationEnabled", true);
        ReflectionTestUtils.setField(xmlValidationService, "ecliValidationEnabled", true);
        ReflectionTestUtils.setField(xmlValidationService, "strictMode", false);
    }

    @Test
    void testValidateSimpleXmlDocument() throws Exception {
        String simpleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Test Legal Document</title>
                <content>This is a simple XML document for testing</content>
                <ecli>ECLI:DE:BGH:2024:123</ecli>
            </document>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(simpleXml);
        
        XmlValidationService.ComprehensiveValidationResult result = 
            future.get(10, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.isSanitizationPassed());
        assertTrue(result.isStructureValid());
        assertFalse(result.isLegalDocMLFormat()); // Simple XML is not LegalDocML
        assertEquals(1, result.getEcliIdentifiers().size());
        assertTrue(result.getEcliIdentifiers().contains("ECLI:DE:BGH:2024:123"));
    }

    @Test
    void testValidateLegalDocMLDocument() throws Exception {
        String legalDocMLXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <akomaNtoso xmlns="http://docs.oasis-open.org/legaldocml/ns/akn/3.0">
                <meta>
                    <identification>
                        <FRBRWork>
                            <FRBRthis value="/de/act/1976-07-15/bnatschg"/>
                            <FRBRuri value="/de/act/bnatschg"/>
                            <FRBRdate date="1976-07-15"/>
                            <FRBRauthor href="#bundestag"/>
                            <componentInfo>
                                <componentData id="cmp_1" href="test.xml" showAs="Test"/>
                            </componentInfo>
                        </FRBRWork>
                        <FRBRExpression>
                            <FRBRthis value="/de/act/1976-07-15/bnatschg/de"/>
                            <FRBRuri value="/de/act/bnatschg/de"/>
                            <FRBRdate date="1976-07-15"/>
                            <FRBRauthor href="#bundestag"/>
                            <FRBRlanguage language="de"/>
                        </FRBRExpression>
                        <FRBRManifestation>
                            <FRBRthis value="/de/act/1976-07-15/bnatschg/de.xml"/>
                            <FRBRuri value="/de/act/bnatschg/de.xml"/>
                            <FRBRdate date="2024-01-01"/>
                            <FRBRauthor href="#bundesdruckerei"/>
                        </FRBRManifestation>
                    </identification>
                    <publication date="1976-07-20" name="BGBl" showAs="BGBl I"/>
                    <lifecycle>
                        <eventRef date="1976-07-15" id="e1" source="#bundestag" type="generation"/>
                    </lifecycle>
                </meta>
                <body>
                    <section eId="sec_1">
                        <heading>§ 1 Test Section</heading>
                        <content>
                            <p eId="sec_1.p_1">This is a test legal document with ECLI:DE:BGH:2024:456</p>
                        </content>
                    </section>
                </body>
            </akomaNtoso>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(legalDocMLXml);
        
        XmlValidationService.ComprehensiveValidationResult result = 
            future.get(15, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.isSanitizationPassed());
        assertTrue(result.isStructureValid());
        assertTrue(result.isLegalDocMLFormat());
        assertEquals(1, result.getEcliIdentifiers().size());
        assertTrue(result.getEcliIdentifiers().contains("ECLI:DE:BGH:2024:456"));
        
        // Check LegalDocML validation result
        assertNotNull(result.getLegalDocMLValidationResult());
        assertTrue(result.getLegalDocMLValidationResult().isValid());
        assertFalse(result.getLegalDocMLValidationResult().hasErrors());
    }

    @Test
    void testQuickValidationSuccess() throws XmlValidationException {
        String validXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Quick Validation Test</title>
                <ecli>ECLI:DE:BAG:2023:789</ecli>
            </document>
            """;
        
        XmlValidationService.ValidationSummary summary = 
            xmlValidationService.quickValidate(validXml);
        
        assertNotNull(summary);
        assertTrue(summary.isValid());
        assertTrue(summary.isSanitizationPassed());
        assertTrue(summary.isStructureValid());
        assertEquals(1, summary.getEcliCount());
        assertNull(summary.getErrorMessage());
    }

    @Test
    void testQuickValidationWithMalformedXml() throws XmlValidationException {
        String malformedXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <unclosed-tag>
                <content>Missing closing tag
            </document>
            """;
        
        XmlValidationService.ValidationSummary summary = 
            xmlValidationService.quickValidate(malformedXml);
        
        assertNotNull(summary);
        assertFalse(summary.isValid());
        assertFalse(summary.isSanitizationPassed());
        assertFalse(summary.isStructureValid());
        assertNotNull(summary.getErrorMessage());
    }

    @Test
    void testValidationWithMultipleEclis() throws Exception {
        String xmlWithMultipleEclis = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Document with Multiple ECLIs</title>
                <case id="ECLI:DE:BGH:2024:100">
                    <content>Main case</content>
                </case>
                <reference>
                    <related>See ECLI:DE:BAG:2023:200 for employment law aspects</related>
                    <related>Also compare with ECLI:EU:C:2022:300</related>
                </reference>
            </document>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(xmlWithMultipleEclis);
        
        XmlValidationService.ComprehensiveValidationResult result = 
            future.get(10, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(3, result.getEcliIdentifiers().size());
        assertTrue(result.getEcliIdentifiers().contains("ECLI:DE:BGH:2024:100"));
        assertTrue(result.getEcliIdentifiers().contains("ECLI:DE:BAG:2023:200"));
        assertTrue(result.getEcliIdentifiers().contains("ECLI:EU:C:2022:300"));
    }

    @Test
    void testValidationInStrictMode() {
        // Enable strict mode
        ReflectionTestUtils.setField(xmlValidationService, "strictMode", true);
        
        String xmlWithSecurityIssue = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE document [
                <!ENTITY test "test entity">
            ]>
            <document>
                <content>&test;</content>
            </document>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(xmlWithSecurityIssue);
        
        assertThrows(Exception.class, () -> {
            future.get(10, TimeUnit.SECONDS);
        });
    }

    @Test
    void testValidationWithDisabledComponents() throws Exception {
        // Disable specific validation components
        ReflectionTestUtils.setField(xmlValidationService, "legalDocMLValidationEnabled", false);
        ReflectionTestUtils.setField(xmlValidationService, "ecliValidationEnabled", false);
        
        String simpleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Test with disabled components</title>
                <ecli>ECLI:DE:BGH:2024:999</ecli>
            </document>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(simpleXml);
        
        XmlValidationService.ComprehensiveValidationResult result = 
            future.get(10, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        // ECLI validation disabled, so no ECLIs should be found
        assertEquals(0, result.getEcliIdentifiers().size());
    }

    @Test
    void testValidationResultSummary() throws Exception {
        String testXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Summary Test Document</title>
                <content>Content with substantial text for analysis</content>
                <ecli>ECLI:DE:BGH:2024:SUMMARY</ecli>
            </document>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(testXml);
        
        XmlValidationService.ComprehensiveValidationResult result = 
            future.get(10, TimeUnit.SECONDS);
        
        assertNotNull(result);
        
        String summary = result.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Valid=true"));
        assertTrue(summary.contains("Sanitization=true"));
        assertTrue(summary.contains("Structure=true"));
        assertTrue(summary.contains("ECLIs=1"));
        
        // Test size calculations
        assertTrue(result.getOriginalSize() > 0);
        assertTrue(result.getSanitizedSize() > 0);
        assertTrue(result.getElementCount() > 0);
        assertTrue(result.isHasSubstantialContent());
    }

    @Test
    void testValidationWithComplexLegalDocument() throws Exception {
        String complexLegalXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <judgment xmlns="http://docs.oasis-open.org/legaldocml/ns/akn/3.0">
                <meta>
                    <identification>
                        <FRBRWork>
                            <FRBRthis value="/de/judgment/bgh/2024-01-15/1-zr-123-23"/>
                            <FRBRuri value="/de/judgment/bgh/1-zr-123-23"/>
                            <FRBRdate date="2024-01-15"/>
                            <FRBRauthor href="#bgh"/>
                        </FRBRWork>
                        <FRBRExpression>
                            <FRBRthis value="/de/judgment/bgh/2024-01-15/1-zr-123-23/de"/>
                            <FRBRuri value="/de/judgment/bgh/1-zr-123-23/de"/>
                            <FRBRdate date="2024-01-15"/>
                            <FRBRauthor href="#bgh"/>
                            <FRBRlanguage language="de"/>
                        </FRBRExpression>
                        <FRBRManifestation>
                            <FRBRthis value="/de/judgment/bgh/2024-01-15/1-zr-123-23/de.xml"/>
                            <FRBRuri value="/de/judgment/bgh/1-zr-123-23/de.xml"/>
                            <FRBRdate date="2024-01-15"/>
                            <FRBRauthor href="#bundesdruckerei"/>
                        </FRBRManifestation>
                    </identification>
                    <publication date="2024-01-20" name="BGH-Website"/>
                    <lifecycle>
                        <eventRef date="2024-01-15" id="decision" source="#bgh" type="decision"/>
                    </lifecycle>
                </meta>
                <preface>
                    <courtType>Bundesgerichtshof</courtType>
                    <docketNumber eId="docket">I ZR 123/23</docketNumber>
                    <decisionDate date="2024-01-15"/>
                    <judges>
                        <judge role="president">Dr. Mustermann</judge>
                        <judge>Prof. Dr. Beispiel</judge>
                    </judges>
                </preface>
                <body eId="body">
                    <section eId="facts" GUID="12345678-1234-1234-1234-123456789abc">
                        <heading>Sachverhalt</heading>
                        <content>
                            <p eId="facts.p1">Der Kläger macht Ansprüche geltend...</p>
                            <p eId="facts.p2">Verwiesen wird auf ECLI:DE:BGH:2023:456 und ECLI:DE:OLG:2022:789</p>
                        </content>
                    </section>
                    <section eId="reasoning" wId="reasoning-stable-id">
                        <heading>Entscheidungsgründe</heading>
                        <content>
                            <p eId="reasoning.p1">Das Berufungsgericht hat zu Recht erkannt...</p>
                        </content>
                    </section>
                </body>
                <conclusions>
                    <p>Die Revision wird zurückgewiesen.</p>
                </conclusions>
            </judgment>
            """;
        
        CompletableFuture<XmlValidationService.ComprehensiveValidationResult> future = 
            xmlValidationService.validateLegalDocument(complexLegalXml);
        
        XmlValidationService.ComprehensiveValidationResult result = 
            future.get(15, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isValid());
        assertTrue(result.isSanitizationPassed());
        assertTrue(result.isStructureValid());
        assertTrue(result.isLegalDocMLFormat());
        
        // Should detect multiple ECLIs in the references
        assertTrue(result.getEcliIdentifiers().size() >= 2);
        assertTrue(result.getEcliIdentifiers().contains("ECLI:DE:BGH:2023:456"));
        assertTrue(result.getEcliIdentifiers().contains("ECLI:DE:OLG:2022:789"));
        
        // Check LegalDocML-specific validation
        LegalDocMLValidator.LegalDocMLValidationResult legalResult = result.getLegalDocMLValidationResult();
        assertNotNull(legalResult);
        assertTrue(legalResult.isValid());
        
        // Should have substantial content and multiple elements
        assertTrue(result.isHasSubstantialContent());
        assertTrue(result.getElementCount() > 10);
        
        // Document type should be detected
        assertNotNull(result.getDocumentType());
    }
}