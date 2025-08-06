package de.legal.crawler.validator;

import de.legal.crawler.exception.XmlValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ECLI validator
 */
class EcliValidatorTest {

    private EcliValidator ecliValidator;

    @BeforeEach
    void setUp() {
        ecliValidator = new EcliValidator();
    }

    @Test
    void testValidGermanEcli() throws XmlValidationException {
        String validEcli = "ECLI:DE:BGH:2024:123";
        
        EcliValidator.ValidationResult result = ecliValidator.validateEcli(validEcli);
        
        assertTrue(result.isValid());
        assertEquals("ECLI:DE:BGH:2024:123", result.getNormalizedEcli());
        assertEquals("DE", result.getComponents().getCountryCode());
        assertEquals("BGH", result.getComponents().getCourtCode());
        assertEquals("2024", result.getComponents().getYear());
        assertEquals("123", result.getComponents().getOrdinalNumber());
        assertTrue(result.getComponents().isGerman());
    }

    @Test
    void testValidEuropeanUnionEcli() throws XmlValidationException {
        String validEcli = "EU:C:2005:446";
        
        EcliValidator.ValidationResult result = ecliValidator.validateEcli(validEcli);
        
        assertTrue(result.isValid());
        assertEquals("EU:C:2005:446", result.getNormalizedEcli());
        assertEquals("EU", result.getComponents().getCountryCode());
        assertTrue(result.getComponents().isEuropeanUnion());
    }

    @Test
    void testEcliWithoutPrefix() throws XmlValidationException {
        String ecliWithoutPrefix = "DE:BGH:2024:ABC.123";
        
        EcliValidator.ValidationResult result = ecliValidator.validateEcli(ecliWithoutPrefix);
        
        assertTrue(result.isValid());
        assertEquals("ECLI:DE:BGH:2024:ABC.123", result.getNormalizedEcli());
    }

    @Test
    void testCaseInsensitiveEcli() throws XmlValidationException {
        String lowercaseEcli = "ecli:de:bgh:2024:test";
        
        EcliValidator.ValidationResult result = ecliValidator.validateEcli(lowercaseEcli);
        
        assertTrue(result.isValid());
        assertEquals("ECLI:DE:BGH:2024:TEST", result.getNormalizedEcli());
    }

    @Test
    void testInvalidCountryCode() {
        String invalidEcli = "ECLI:XX:BGH:2024:123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testInvalidCourtCodeTooLong() {
        String invalidEcli = "ECLI:DE:VERYLONGCOURTCODE:2024:123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testInvalidCourtCodeStartsWithNumber() {
        String invalidEcli = "ECLI:DE:1BGH:2024:123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testInvalidYear() {
        String invalidEcli = "ECLI:DE:BGH:99:123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testYearOutOfRange() {
        String invalidEcli = "ECLI:DE:BGH:1800:123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testFutureYear() {
        int futureYear = java.time.Year.now().getValue() + 2;
        String invalidEcli = "ECLI:DE:BGH:" + futureYear + ":123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testInvalidOrdinalNumberTooLong() {
        String invalidEcli = "ECLI:DE:BGH:2024:VERYLONGORDINALNUMBERTHATEXCEEDSLIMIT";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testInvalidOrdinalNumberWithSpecialChars() {
        String invalidEcli = "ECLI:DE:BGH:2024:ABC-123";
        
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(invalidEcli);
        });
    }

    @Test
    void testNullEcli() {
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli(null);
        });
    }

    @Test
    void testEmptyEcli() {
        assertThrows(XmlValidationException.class, () -> {
            ecliValidator.validateEcli("");
        });
    }

    @Test
    void testExtractEcliFromXml() {
        String xmlContent = """
            <document>
                <case id="ECLI:DE:BGH:2024:123"/>
                <reference>See also ECLI:DE:BAG:2023:456</reference>
                <invalid>This is not an ECLI: INVALID:FORMAT</invalid>
                <eu-case>EU:C:2005:446</eu-case>
            </document>
            """;
        
        Set<String> ecliIdentifiers = ecliValidator.extractEcliFromXml(xmlContent);
        
        assertEquals(3, ecliIdentifiers.size());
        assertTrue(ecliIdentifiers.contains("ECLI:DE:BGH:2024:123"));
        assertTrue(ecliIdentifiers.contains("ECLI:DE:BAG:2023:456"));
        assertTrue(ecliIdentifiers.contains("EU:C:2005:446"));
    }

    @Test
    void testIsGermanCourt() throws XmlValidationException {
        assertTrue(ecliValidator.isGermanCourt("ECLI:DE:BGH:2024:123"));
        assertFalse(ecliValidator.isGermanCourt("ECLI:FR:CASS:2024:123"));
        assertFalse(ecliValidator.isGermanCourt("EU:C:2005:446"));
    }

    @Test
    void testGermanCourtCodes() throws XmlValidationException {
        String[] germanCourts = {"BAG", "BGH", "BSG", "BVERWG", "BFH", "BVERFG", "LAG", "OLG"};
        
        for (String court : germanCourts) {
            String ecli = "ECLI:DE:" + court + ":2024:123";
            EcliValidator.ValidationResult result = ecliValidator.validateEcli(ecli);
            assertTrue(result.isValid(), "Court code " + court + " should be valid");
        }
    }

    @Test
    void testComplexOrdinalNumbers() throws XmlValidationException {
        String[] validOrdinals = {"123", "ABC", "A.123", "TEST.CASE.1", "2024.01.15"};
        
        for (String ordinal : validOrdinals) {
            String ecli = "ECLI:DE:BGH:2024:" + ordinal;
            EcliValidator.ValidationResult result = ecliValidator.validateEcli(ecli);
            assertTrue(result.isValid(), "Ordinal " + ordinal + " should be valid");
        }
    }

    @Test
    void testEcliComponentsToString() throws XmlValidationException {
        String ecli = "ECLI:DE:BGH:2024:123";
        EcliValidator.ValidationResult result = ecliValidator.validateEcli(ecli);
        
        String componentString = result.getComponents().toString();
        assertNotNull(componentString);
        assertTrue(componentString.contains("DE"));
        assertTrue(componentString.contains("BGH"));
        assertTrue(componentString.contains("2024"));
        assertTrue(componentString.contains("123"));
    }

    @Test
    void testValidationResultToString() throws XmlValidationException {
        String ecli = "ECLI:DE:BGH:2024:123";
        EcliValidator.ValidationResult result = ecliValidator.validateEcli(ecli);
        
        String resultString = result.toString();
        assertNotNull(resultString);
        assertTrue(resultString.contains("ECLI:DE:BGH:2024:123"));
        assertTrue(resultString.contains("true"));
    }
}