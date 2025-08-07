package de.legal.crawler.service;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.repository.LegalDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central orchestration service that coordinates all crawling activities
 * Manages the complete workflow from sitemap discovery to document storage
 */
@Service
public class CrawlerOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerOrchestrationService.class);
    
    private static final Pattern DOCUMENT_ID_PATTERN = Pattern.compile("docid=([A-Z0-9]+)");
    
    @Autowired
    private SitemapCrawlerService sitemapCrawlerService;
    
    @Autowired
    private DocumentDownloadService downloadService;
    
    @Autowired
    @Qualifier("legalDocumentRepository")
    private LegalDocumentRepository documentRepository;
    
    /**
     * Main crawling orchestration method
     */
    @Async
    public CompletableFuture<CrawlResult> startCrawling(LocalDate date, boolean forceUpdate) {
        logger.info("Starting crawling orchestration for date: {}", date);
        
        AtomicInteger newDocuments = new AtomicInteger(0);
        AtomicInteger updatedDocuments = new AtomicInteger(0);
        AtomicInteger failedDocuments = new AtomicInteger(0);
        
        try {
            // Phase 1: Fetch sitemap index
            List<String> sitemapUrls = sitemapCrawlerService.fetchSitemapIndex(date).get();
            logger.info("Found {} sitemaps for date {}", sitemapUrls.size(), date);
            
            // Phase 2: Process each sitemap
            for (String sitemapUrl : sitemapUrls) {
                try {
                    List<SitemapCrawlerService.DocumentEntry> documentEntries = 
                        sitemapCrawlerService.fetchSitemap(sitemapUrl).get();
                    
                    logger.info("Processing {} documents from sitemap: {}", 
                              documentEntries.size(), sitemapUrl);
                    
                    // Phase 3: Process each document
                    for (SitemapCrawlerService.DocumentEntry entry : documentEntries) {
                        try {
                            processDocumentEntry(entry, forceUpdate, 
                                               newDocuments, updatedDocuments, failedDocuments);
                        } catch (Exception e) {
                            logger.error("Failed to process document entry: {}", entry.getUrl(), e);
                            failedDocuments.incrementAndGet();
                        }
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to process sitemap: {}", sitemapUrl, e);
                }
            }
            
            CrawlResult result = new CrawlResult(
                date, 
                newDocuments.get(), 
                updatedDocuments.get(), 
                failedDocuments.get()
            );
            
            logger.info("Crawling completed for {}: {} new, {} updated, {} failed", 
                       date, result.getNewDocuments(), result.getUpdatedDocuments(), result.getFailedDocuments());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            logger.error("Crawling orchestration failed for date {}: {}", date, e.getMessage(), e);
            throw new RuntimeException("Crawling failed", e);
        }
    }
    
    private void processDocumentEntry(SitemapCrawlerService.DocumentEntry entry, boolean forceUpdate,
                                    AtomicInteger newDocs, AtomicInteger updatedDocs, AtomicInteger failedDocs) {
        
        String documentId = extractDocumentId(entry.getUrl());
        if (documentId == null) {
            logger.warn("Could not extract document ID from URL: {}", entry.getUrl());
            failedDocs.incrementAndGet();
            return;
        }
        
        // Check if document already exists
        LegalDocument existingDocument = documentRepository.findByDocumentId(documentId).orElse(null);
        
        if (existingDocument != null && !forceUpdate && 
            existingDocument.getStatus() == LegalDocument.DocumentStatus.DOWNLOADED) {
            logger.debug("Document {} already exists and is downloaded, skipping", documentId);
            return;
        }
        
        // Create or update document
        LegalDocument document = existingDocument != null ? existingDocument : new LegalDocument();
        
        if (existingDocument == null) {
            // New document
            document.setDocumentId(documentId);
            document.setSourceUrl(entry.getUrl());
            document.setCourt(extractCourt(documentId));
            document.setDecisionDate(parseDecisionDate(entry));
            document.setStatus(LegalDocument.DocumentStatus.PENDING);
            
            document = documentRepository.save(document);
            newDocs.incrementAndGet();
            logger.debug("Created new document: {}", documentId);
        } else {
            updatedDocs.incrementAndGet();
            logger.debug("Updating existing document: {}", documentId);
        }
        
        // Download document content
        try {
            downloadService.downloadDocument(document).get();
            documentRepository.save(document);
            logger.debug("Successfully downloaded document: {}", documentId);
            
        } catch (Exception e) {
            logger.error("Failed to download document {}: {}", documentId, e.getMessage());
            document.setStatus(LegalDocument.DocumentStatus.FAILED);
            documentRepository.save(document);
            failedDocs.incrementAndGet();
        }
    }
    
    private String extractDocumentId(String url) {
        Matcher matcher = DOCUMENT_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    private String extractCourt(String documentId) {
        // Extract court code from document ID pattern
        if (documentId.startsWith("KARE")) return "BAG"; // Bundesarbeitsgericht
        if (documentId.startsWith("KORE")) return "BGH"; // Bundesgerichtshof  
        if (documentId.startsWith("KSRE")) return "BSG"; // Bundessozialgericht
        if (documentId.startsWith("WBRE")) return "BVerwG"; // Bundesverwaltungsgericht
        
        // Default fallback - try to extract from document content later
        return "UNKNOWN";
    }
    
    private LocalDateTime parseDecisionDate(SitemapCrawlerService.DocumentEntry entry) {
        // For now, use current date - this should be extracted from document content
        // in a real implementation after downloading
        return LocalDateTime.now();
    }
    
    /**
     * Retry failed documents
     */
    @Async
    public CompletableFuture<Integer> retryFailedDocuments() {
        LocalDateTime retryAfter = LocalDateTime.now().minusHours(1);
        List<LegalDocument> failedDocs = documentRepository.findFailedDocumentsForRetry(retryAfter);
        
        logger.info("Retrying {} failed documents", failedDocs.size());
        
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (LegalDocument document : failedDocs) {
            try {
                document.setStatus(LegalDocument.DocumentStatus.PENDING);
                downloadService.downloadDocument(document).get();
                documentRepository.save(document);
                successCount.incrementAndGet();
                
            } catch (Exception e) {
                logger.error("Retry failed for document {}: {}", document.getDocumentId(), e.getMessage());
                document.setStatus(LegalDocument.DocumentStatus.FAILED);
                documentRepository.save(document);
            }
        }
        
        logger.info("Retry completed: {} of {} documents succeeded", successCount.get(), failedDocs.size());
        return CompletableFuture.completedFuture(successCount.get());
    }
    
    /**
     * Crawl result summary
     */
    public static class CrawlResult {
        private final LocalDate date;
        private final int newDocuments;
        private final int updatedDocuments;
        private final int failedDocuments;
        
        public CrawlResult(LocalDate date, int newDocuments, int updatedDocuments, int failedDocuments) {
            this.date = date;
            this.newDocuments = newDocuments;
            this.updatedDocuments = updatedDocuments;
            this.failedDocuments = failedDocuments;
        }
        
        public LocalDate getDate() { return date; }
        public int getNewDocuments() { return newDocuments; }
        public int getUpdatedDocuments() { return updatedDocuments; }
        public int getFailedDocuments() { return failedDocuments; }
        public int getTotalProcessed() { return newDocuments + updatedDocuments; }
    }
}