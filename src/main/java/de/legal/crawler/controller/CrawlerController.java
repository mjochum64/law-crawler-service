package de.legal.crawler.controller;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.service.CrawlerOrchestrationService;
import de.legal.crawler.service.DocumentDownloadService;
import de.legal.crawler.service.SitemapDiscoveryService;
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
    @Qualifier("legalDocumentRepository")
    private LegalDocumentRepository documentRepository;
    
    @Autowired
    private DocumentDownloadService downloadService;
    
    @Autowired
    private SitemapDiscoveryService discoveryService;
    
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
     * Simple search documents by title
     */
    @GetMapping("/search/simple")
    public ResponseEntity<List<LegalDocument>> searchDocumentsByTitle(
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
    
    /**
     * Test sitemap discovery optimizations
     */
    @GetMapping("/test/sitemap-optimizations")
    public ResponseEntity<Map<String, Object>> testSitemapOptimizations() {
        try {
            SitemapDiscoveryService.SitemapValidationResult result = discoveryService.validateOptimizations();
            
            return ResponseEntity.ok(Map.of(
                "summary", result.getSummary(),
                "details", result.getEntries(),
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to test sitemap optimizations",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Full-text search in legal documents
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String court) {
        
        try {
            // Build Solr query for full-text search
            StringBuilder solrQuery = new StringBuilder();
            
            // Search in multiple fields
            solrQuery.append("(full_text:").append(escapeQueryValue(query))
                    .append(" OR title:").append(escapeQueryValue(query))
                    .append(" OR subject:").append(escapeQueryValue(query))
                    .append(" OR case_number:").append(escapeQueryValue(query))
                    .append(" OR norms:").append(escapeQueryValue(query))
                    .append(")");
            
            // Add court filter if specified
            if (court != null && !court.isEmpty()) {
                solrQuery.append(" AND court:").append(escapeQueryValue(court));
            }
            
            List<LegalDocument> documents = documentRepository.findAllWithQueryAndSort(
                solrQuery.toString(), "decision_date desc");
            
            // Apply pagination
            int start = page * size;
            int end = Math.min(start + size, documents.size());
            List<LegalDocument> pagedDocuments = documents.subList(
                Math.min(start, documents.size()), end);
            
            return ResponseEntity.ok(Map.of(
                "query", query,
                "court", court != null ? court : "all",
                "page", page,
                "size", size,
                "total", documents.size(),
                "documents", pagedDocuments.stream().map(doc -> Map.of(
                    "documentId", doc.getDocumentId(),
                    "title", doc.getTitle() != null ? doc.getTitle() : "",
                    "subject", doc.getSubject() != null ? doc.getSubject() : "",
                    "court", doc.getCourt(),
                    "caseNumber", doc.getCaseNumber() != null ? doc.getCaseNumber() : "",
                    "documentType", doc.getDocumentType() != null ? doc.getDocumentType() : "",
                    "decisionDate", doc.getDecisionDate(),
                    "ecli", doc.getEcliIdentifier() != null ? doc.getEcliIdentifier() : "",
                    "norms", doc.getNorms() != null ? doc.getNorms() : ""
                )).toList(),
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Search failed",
                "message", e.getMessage(),
                "query", query
            ));
        }
    }
    
    private String escapeQueryValue(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}