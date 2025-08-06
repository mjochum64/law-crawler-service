package de.legal.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SitemapCrawlerService
 */
@SpringBootTest
@ActiveProfiles("test")
class SitemapCrawlerServiceTest {

    private SitemapCrawlerService sitemapCrawlerService;

    @BeforeEach
    void setUp() {
        sitemapCrawlerService = new SitemapCrawlerService();
    }

    @Test
    void testDocumentEntryExtractions() {
        // Test DocumentEntry functionality
        SitemapCrawlerService.DocumentEntry entry = new SitemapCrawlerService.DocumentEntry(
            "https://www.rechtsprechung-im-internet.de/jportal/?quelle=jlink&docid=KARE600068723&psml=bsjrsprod.psml&max=true",
            "2024-08-06T10:00:00Z"
        );
        
        assertNotNull(entry.getUrl());
        assertNotNull(entry.getLastModified());
        assertEquals("KARE600068723", entry.extractDocumentId());
    }

    @Test
    void testDocumentIdExtraction() {
        SitemapCrawlerService.DocumentEntry entry1 = new SitemapCrawlerService.DocumentEntry(
            "https://www.rechtsprechung-im-internet.de/jportal/?quelle=jlink&docid=KORE301022024&psml=bsjrsprod.psml&max=true",
            null
        );
        
        assertEquals("KORE301022024", entry1.extractDocumentId());
        
        SitemapCrawlerService.DocumentEntry entry2 = new SitemapCrawlerService.DocumentEntry(
            "https://www.rechtsprechung-im-internet.de/jportal/invalid-url",
            null
        );
        
        assertNull(entry2.extractDocumentId());
    }

    @Test
    void testFetchSitemapIndexAsync() {
        // Test that the async method returns a CompletableFuture
        CompletableFuture<List<String>> future = sitemapCrawlerService.fetchSitemapIndex(LocalDate.now());
        assertNotNull(future);
        assertFalse(future.isDone()); // Should be async
    }
}