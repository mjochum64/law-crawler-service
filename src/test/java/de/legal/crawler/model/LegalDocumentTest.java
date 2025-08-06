package de.legal.crawler.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LegalDocument model
 */
class LegalDocumentTest {

    @Test
    void testLegalDocumentCreation() {
        LocalDateTime now = LocalDateTime.now();
        LegalDocument document = new LegalDocument("KARE123456", "BAG", "http://example.com", now);
        
        assertEquals("KARE123456", document.getDocumentId());
        assertEquals("BAG", document.getCourt());
        assertEquals("http://example.com", document.getSourceUrl());
        assertEquals(now, document.getDecisionDate());
        assertNotNull(document.getCrawledAt());
        assertEquals(LegalDocument.DocumentStatus.PENDING, document.getStatus());
    }

    @Test
    void testLegalDocumentSetters() {
        LegalDocument document = new LegalDocument();
        
        document.setDocumentId("TEST123");
        document.setCourt("BGH");
        document.setTitle("Test Decision");
        document.setEcliIdentifier("ECLI:DE:BGH:2024:123");
        document.setDocumentType("Decision");
        document.setStatus(LegalDocument.DocumentStatus.DOWNLOADED);
        
        assertEquals("TEST123", document.getDocumentId());
        assertEquals("BGH", document.getCourt());
        assertEquals("Test Decision", document.getTitle());
        assertEquals("ECLI:DE:BGH:2024:123", document.getEcliIdentifier());
        assertEquals("Decision", document.getDocumentType());
        assertEquals(LegalDocument.DocumentStatus.DOWNLOADED, document.getStatus());
    }

    @Test
    void testDocumentStatusEnum() {
        LegalDocument document = new LegalDocument();
        
        // Test all status values
        document.setStatus(LegalDocument.DocumentStatus.PENDING);
        assertEquals(LegalDocument.DocumentStatus.PENDING, document.getStatus());
        
        document.setStatus(LegalDocument.DocumentStatus.DOWNLOADED);
        assertEquals(LegalDocument.DocumentStatus.DOWNLOADED, document.getStatus());
        
        document.setStatus(LegalDocument.DocumentStatus.PROCESSED);
        assertEquals(LegalDocument.DocumentStatus.PROCESSED, document.getStatus());
        
        document.setStatus(LegalDocument.DocumentStatus.FAILED);
        assertEquals(LegalDocument.DocumentStatus.FAILED, document.getStatus());
    }
}