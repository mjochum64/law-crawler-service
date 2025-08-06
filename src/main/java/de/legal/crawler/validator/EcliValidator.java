package de.legal.crawler.validator;

import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.exception.XmlValidationException.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validator for European Case Law Identifier (ECLI) format
 * 
 * ECLI Format: ECLI:[Country Code]:[Court Code]:[Year]:[Ordinal Number]
 * 
 * Rules:
 * - Country Code: 2-letter ISO 3166-1 alpha-2 (with exceptions: UK, EL)
 * - Court Code: 1-7 characters, starts with letter, may contain digits
 * - Year: 4-digit year
 * - Ordinal Number: 1-25 characters, letters, digits, and dots only
 */
@Component
public class EcliValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(EcliValidator.class);
    
    // ECLI validation pattern
    private static final Pattern ECLI_PATTERN = Pattern.compile(
        "^ECLI:[A-Z]{2}:[A-Z][A-Z0-9]{0,6}:\\d{4}:[A-Z0-9.]{1,25}$",
        Pattern.CASE_INSENSITIVE
    );
    
    // Alternative pattern without ECLI prefix (used by EU courts)
    private static final Pattern EU_ECLI_PATTERN = Pattern.compile(
        "^[A-Z]{2}:[A-Z]:\\d{4}:[A-Z0-9.]{1,25}$",
        Pattern.CASE_INSENSITIVE
    );
    
    // Valid European country codes (ISO 3166-1 alpha-2 with exceptions)
    private static final Set<String> VALID_COUNTRY_CODES = Set.of(
        // Standard ISO codes
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", 
        "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        // Special cases
        "EL", "UK", // Greece (EL) and United Kingdom (UK) exceptions
        "EU" // European Union courts
    );
    
    // Common German court codes for validation
    private static final Set<String> GERMAN_COURT_CODES = Set.of(
        "BAG", "BGH", "BSG", "BVERWG", "BPATG", "BFH", "BVERFG",  // Federal courts
        "LAG", "OLG", "LSG", "OVG", "VG", "SG", "FG", "AG"        // State courts
    );
    
    /**
     * Validate ECLI identifier format and content
     * 
     * @param ecliIdentifier The ECLI identifier to validate
     * @return ValidationResult containing validation status and normalized ECLI
     * @throws XmlValidationException if ECLI format is invalid
     */
    public ValidationResult validateEcli(String ecliIdentifier) throws XmlValidationException {
        if (ecliIdentifier == null || ecliIdentifier.trim().isEmpty()) {
            throw new XmlValidationException(
                "ECLI identifier cannot be null or empty",
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
        
        String normalizedEcli = normalizeEcli(ecliIdentifier);
        
        // Validate basic format
        if (!ECLI_PATTERN.matcher(normalizedEcli).matches() && 
            !EU_ECLI_PATTERN.matcher(normalizedEcli).matches()) {
            throw new XmlValidationException(
                "Invalid ECLI format: " + ecliIdentifier,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
        
        // Parse components for detailed validation
        EcliComponents components = parseEcliComponents(normalizedEcli);
        
        // Validate components
        validateCountryCode(components.countryCode);
        validateCourtCode(components.countryCode, components.courtCode);
        validateYear(components.year);
        validateOrdinalNumber(components.ordinalNumber);
        
        logger.debug("ECLI validation successful: {}", normalizedEcli);
        
        return new ValidationResult(normalizedEcli, components, true);
    }
    
    /**
     * Extract and validate ECLI from XML content
     * 
     * @param xmlContent XML content that may contain ECLI identifiers
     * @return Set of valid ECLI identifiers found in the content
     */
    public Set<String> extractEcliFromXml(String xmlContent) {
        Set<String> ecliIdentifiers = new java.util.HashSet<>();
        
        // Pattern to find ECLI identifiers in XML content
        Pattern xmlEcliPattern = Pattern.compile(
            "(?:ECLI:)?[A-Z]{2}:[A-Z][A-Z0-9]{0,6}:\\d{4}:[A-Z0-9.]{1,25}",
            Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = xmlEcliPattern.matcher(xmlContent);
        while (matcher.find()) {
            try {
                String foundEcli = matcher.group();
                ValidationResult result = validateEcli(foundEcli);
                if (result.isValid()) {
                    ecliIdentifiers.add(result.getNormalizedEcli());
                }
            } catch (XmlValidationException e) {
                logger.debug("Invalid ECLI found in XML content: {}", matcher.group());
            }
        }
        
        return ecliIdentifiers;
    }
    
    /**
     * Check if ECLI is from a German court
     */
    public boolean isGermanCourt(String ecliIdentifier) throws XmlValidationException {
        ValidationResult result = validateEcli(ecliIdentifier);
        return "DE".equals(result.getComponents().countryCode);
    }
    
    private String normalizeEcli(String ecliIdentifier) {
        String normalized = ecliIdentifier.trim().toUpperCase();
        
        // Add ECLI prefix if missing (except for EU court format)
        if (!normalized.startsWith("ECLI:") && !normalized.startsWith("EU:")) {
            normalized = "ECLI:" + normalized;
        }
        
        return normalized;
    }
    
    private EcliComponents parseEcliComponents(String normalizedEcli) throws XmlValidationException {
        String[] parts;
        
        if (normalizedEcli.startsWith("ECLI:")) {
            parts = normalizedEcli.substring(5).split(":", 4); // Skip "ECLI:" prefix
        } else {
            parts = normalizedEcli.split(":", 4); // EU format without ECLI prefix
        }
        
        if (parts.length != 4) {
            throw new XmlValidationException(
                "Invalid ECLI component count: " + parts.length,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
        
        return new EcliComponents(parts[0], parts[1], parts[2], parts[3]);
    }
    
    private void validateCountryCode(String countryCode) throws XmlValidationException {
        if (!VALID_COUNTRY_CODES.contains(countryCode)) {
            throw new XmlValidationException(
                "Invalid country code: " + countryCode,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
    }
    
    private void validateCourtCode(String countryCode, String courtCode) throws XmlValidationException {
        if (courtCode.length() < 1 || courtCode.length() > 7) {
            throw new XmlValidationException(
                "Court code must be 1-7 characters: " + courtCode,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
        
        if (!Character.isLetter(courtCode.charAt(0))) {
            throw new XmlValidationException(
                "Court code must start with a letter: " + courtCode,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
        
        // Additional validation for German courts
        if ("DE".equals(countryCode) && !GERMAN_COURT_CODES.contains(courtCode)) {
            logger.debug("Unknown German court code: {}", courtCode);
        }
    }
    
    private void validateYear(String year) throws XmlValidationException {
        try {
            int yearValue = Integer.parseInt(year);
            int currentYear = java.time.Year.now().getValue();
            
            if (yearValue < 1900 || yearValue > currentYear + 1) {
                throw new XmlValidationException(
                    "Year out of reasonable range: " + yearValue,
                    ValidationError.ECLI_VALIDATION_FAILED
                );
            }
        } catch (NumberFormatException e) {
            throw new XmlValidationException(
                "Invalid year format: " + year,
                e,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
    }
    
    private void validateOrdinalNumber(String ordinalNumber) throws XmlValidationException {
        if (ordinalNumber.length() < 1 || ordinalNumber.length() > 25) {
            throw new XmlValidationException(
                "Ordinal number must be 1-25 characters: " + ordinalNumber,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
        
        // Verify only allowed characters (letters, digits, dots)
        if (!ordinalNumber.matches("[A-Z0-9.]+")) {
            throw new XmlValidationException(
                "Ordinal number contains invalid characters: " + ordinalNumber,
                ValidationError.ECLI_VALIDATION_FAILED
            );
        }
    }
    
    /**
     * ECLI components data structure
     */
    public static class EcliComponents {
        private final String countryCode;
        private final String courtCode;
        private final String year;
        private final String ordinalNumber;
        
        public EcliComponents(String countryCode, String courtCode, String year, String ordinalNumber) {
            this.countryCode = countryCode;
            this.courtCode = courtCode;
            this.year = year;
            this.ordinalNumber = ordinalNumber;
        }
        
        public String getCountryCode() { return countryCode; }
        public String getCourtCode() { return courtCode; }
        public String getYear() { return year; }
        public String getOrdinalNumber() { return ordinalNumber; }
        
        public boolean isGerman() {
            return "DE".equals(countryCode);
        }
        
        public boolean isEuropeanUnion() {
            return "EU".equals(countryCode);
        }
        
        @Override
        public String toString() {
            return String.format("EcliComponents{country='%s', court='%s', year='%s', ordinal='%s'}",
                               countryCode, courtCode, year, ordinalNumber);
        }
    }
    
    /**
     * ECLI validation result
     */
    public static class ValidationResult {
        private final String normalizedEcli;
        private final EcliComponents components;
        private final boolean valid;
        
        public ValidationResult(String normalizedEcli, EcliComponents components, boolean valid) {
            this.normalizedEcli = normalizedEcli;
            this.components = components;
            this.valid = valid;
        }
        
        public String getNormalizedEcli() { return normalizedEcli; }
        public EcliComponents getComponents() { return components; }
        public boolean isValid() { return valid; }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{ecli='%s', valid=%s, components=%s}",
                               normalizedEcli, valid, components);
        }
    }
}