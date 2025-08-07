package de.legal.crawler.controller;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.service.CrawlerOrchestrationService;
import de.legal.crawler.service.DocumentDownloadService;
import de.legal.crawler.repository.LegalDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for crawler operations and document management
 */
@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    @Autowired
    private CrawlerOrchestrationService orchestrationService;
    
    @Autowired
    private LegalDocumentRepository documentRepository;
    
    @Autowired
    private DocumentDownloadService downloadService;
    
    /**
     * Start crawling for a specific date
     */
    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> startCrawl(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "false") boolean forceUpdate) {
        
        try {
            LocalDate crawlDate = date != null ? 
                LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) : 
                LocalDate.now().minusDays(1);
            
            orchestrationService.startCrawling(crawlDate, forceUpdate);
            
            return ResponseEntity.ok(Map.of(
                "message", "Crawling started successfully",
                "date", crawlDate.toString(),
                "forceUpdate", forceUpdate,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to start crawling",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Get crawler status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCrawlerStatus() {
        DocumentDownloadService.StorageStats stats = downloadService.getStorageStats();
        
        long totalDocuments = documentRepository.count();
        long pendingDocuments = documentRepository.findByStatus(LegalDocument.DocumentStatus.PENDING).size();
        long downloadedDocuments = documentRepository.findByStatus(LegalDocument.DocumentStatus.DOWNLOADED).size();
        long failedDocuments = documentRepository.findByStatus(LegalDocument.DocumentStatus.FAILED).size();
        
        return ResponseEntity.ok(Map.of(
            "totalDocuments", totalDocuments,
            "pendingDocuments", pendingDocuments,
            "downloadedDocuments", downloadedDocuments,
            "failedDocuments", failedDocuments,
            "storageStats", Map.of(
                "fileCount", stats.getFileCount(),
                "totalSizeMB", Math.round(stats.getTotalSizeMB() * 100.0) / 100.0
            ),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Get documents by court
     */
    @GetMapping("/documents/{court}")
    public ResponseEntity<List<LegalDocument>> getDocumentsByCourt(
            @PathVariable String court,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<LegalDocument> documents = documentRepository
            .findByCourtOrderByDecisionDateDesc(court.toUpperCase(), PageRequest.of(page, size));
            
        return ResponseEntity.ok(documents.getContent());
    }
    
    /**
     * Get document statistics by court
     */
    @GetMapping("/statistics/courts")
    public ResponseEntity<List<Map<String, Object>>> getCourtStatistics() {
        List<Object[]> courtCounts = documentRepository.countDocumentsByCourt();
        
        List<Map<String, Object>> statistics = courtCounts.stream()
            .map(row -> Map.<String, Object>of(
                "court", row[0],
                "count", row[1]
            ))
            .toList();
            
        return ResponseEntity.ok(statistics);
    }
    
    /**
     * Search documents
     */
    @GetMapping("/search")
    public ResponseEntity<List<LegalDocument>> searchDocuments(
            @RequestParam String query) {
        
        List<LegalDocument> documents = documentRepository
            .findByTitleContainingIgnoreCase(query);
            
        return ResponseEntity.ok(documents);
    }
    
    /**
     * Retry failed documents
     */
    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailedDocuments() {
        try {
            orchestrationService.retryFailedDocuments();
            
            return ResponseEntity.ok(Map.of(
                "message", "Retry initiated for failed documents",
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to retry documents",
                "message", e.getMessage()
            ));
        }
    }
}