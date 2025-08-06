package de.legal.crawler.service;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.service.XmlValidationService.ComprehensiveValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for downloading XML content of legal documents
 * Implements structured storage and rate limiting
 */
@Service
public class DocumentDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDownloadService.class);
    
    @Autowired
    private XmlValidationService xmlValidationService;
    
    @Value("${crawler.storage.base-path:./legal-documents}")
    private String basePath;
    
    @Value("${crawler.user-agent:LegalDocumentCrawler/1.0}")
    private String userAgent;
    
    @Value("${crawler.rate-limit-ms:1000}")
    private long rateLimitMs;
    
    @Value("${validation.xml.async-validation:true}")
    private boolean asyncValidation;
    
    @Value("${validation.xml.validation-timeout-seconds:30}")
    private long validationTimeoutSeconds;
    
    @Value("${validation.xml.strict-mode:false}")
    private boolean strictValidationMode;
    
    private final HttpClient httpClient;
    
    public DocumentDownloadService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Download XML content for a legal document
     */
    public CompletableFuture<String> downloadDocument(LegalDocument document) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Downloading document: {} from {}", document.getDocumentId(), document.getSourceUrl());
                
                // Rate limiting
                Thread.sleep(rateLimitMs);
                
                String xmlContent = fetchDocumentContent(document.getSourceUrl());
                String filePath = storeDocument(document, xmlContent);
                
                // Update document with file path
                document.setFilePath(filePath);
                document.setStatus(LegalDocument.DocumentStatus.DOWNLOADED);
                document.setCrawledAt(LocalDateTime.now());
                
                logger.info("Successfully downloaded document: {}", document.getDocumentId());
                return filePath;
                
            } catch (Exception e) {
                logger.error("Failed to download document {}: {}", document.getDocumentId(), e.getMessage());
                document.setStatus(LegalDocument.DocumentStatus.FAILED);
                throw new RuntimeException("Download failed for document: " + document.getDocumentId(), e);
            }
        });
    }
    
    private String fetchDocumentContent(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .header("Accept", "application/xml, text/xml, */*")
            .GET()
            .build();
            
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " for URL: " + url);
        }
        
        return response.body();
    }
    
    private String storeDocument(LegalDocument document, String xmlContent) throws IOException {
        // Create structured directory path: court/year/month/
        Path documentPath = createDocumentPath(document);
        
        // Ensure directory exists
        Files.createDirectories(documentPath.getParent());
        
        // Write XML content
        Files.writeString(documentPath, xmlContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        logger.debug("Stored document {} at {}", document.getDocumentId(), documentPath);
        return documentPath.toString();
    }
    
    private Path createDocumentPath(LegalDocument document) {
        // Structure: base-path/court/year/month/documentId.xml
        LocalDateTime decisionDate = document.getDecisionDate();
        
        String court = document.getCourt().toLowerCase();
        String year = String.valueOf(decisionDate.getYear());
        String month = String.format("%02d", decisionDate.getMonthValue());
        String filename = document.getDocumentId() + ".xml";
        
        return Paths.get(basePath, court, year, month, filename);
    }
    
    /**
     * Get storage statistics
     */
    public StorageStats getStorageStats() {
        try {
            Path baseStoragePath = Paths.get(basePath);
            if (!Files.exists(baseStoragePath)) {
                return new StorageStats(0, 0L);
            }
            
            long fileCount = Files.walk(baseStoragePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .count();
                
            long totalSize = Files.walk(baseStoragePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
                
            return new StorageStats(fileCount, totalSize);
            
        } catch (IOException e) {
            logger.error("Failed to calculate storage stats: {}", e.getMessage());
            return new StorageStats(0, 0L);
        }
    }
    
    public static class StorageStats {
        private final long fileCount;
        private final long totalSizeBytes;
        
        public StorageStats(long fileCount, long totalSizeBytes) {
            this.fileCount = fileCount;
            this.totalSizeBytes = totalSizeBytes;
        }
        
        public long getFileCount() { return fileCount; }
        public long getTotalSizeBytes() { return totalSizeBytes; }
        public double getTotalSizeMB() { return totalSizeBytes / (1024.0 * 1024.0); }
    }
}