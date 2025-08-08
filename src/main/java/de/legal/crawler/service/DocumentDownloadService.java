package de.legal.crawler.service;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.repository.LegalDocumentRepository;
import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.service.XmlValidationService.ComprehensiveValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
    
    @Autowired
    private HtmlContentExtractionService htmlContentExtractionService;
    
    @Autowired
    @Qualifier("legalDocumentRepository") 
    private LegalDocumentRepository documentRepository;
    
    @Autowired
    private Environment environment;
    
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
     * Download XML content for a legal document with validation
     */
    public CompletableFuture<String> downloadDocument(LegalDocument document) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Downloading document: {} from {}", document.getDocumentId(), document.getSourceUrl());
                
                // Rate limiting
                Thread.sleep(rateLimitMs);
                
                String xmlContent = fetchDocumentContent(document.getSourceUrl());
                
                // Perform XML validation
                CompletableFuture<ComprehensiveValidationResult> validationFuture = 
                    performXmlValidation(xmlContent, document);
                
                // Store document (validation may run async)
                String filePath = storeDocument(document, xmlContent);
                
                // Wait for validation if running synchronously or in strict mode
                if (!asyncValidation || strictValidationMode) {
                    ComprehensiveValidationResult validationResult = validationFuture.get(
                        validationTimeoutSeconds, TimeUnit.SECONDS
                    );
                    processValidationResult(document, validationResult);
                } else {
                    // Handle validation result asynchronously
                    validationFuture.whenComplete((validationResult, throwable) -> {
                        if (throwable != null) {
                            logger.warn("Async validation failed for document {}: {}", 
                                      document.getDocumentId(), throwable.getMessage());
                        } else {
                            processValidationResult(document, validationResult);
                        }
                    });
                }
                
                // Extract content from HTML for indexing
                try {
                    HtmlContentExtractionService.ExtractedContent extractedContent = 
                        htmlContentExtractionService.extractContent(xmlContent);
                    
                    // Update document with extracted content
                    document.setTitle(extractedContent.getTitle());
                    document.setSubject(extractedContent.getSubject());
                    document.setFullText(extractedContent.getFullText());
                    document.setCaseNumber(extractedContent.getCaseNumber());
                    document.setEcliIdentifier(extractedContent.getEcli());
                    document.setDocumentType(extractedContent.getDocumentType());
                    document.setNorms(extractedContent.getNorms());
                    
                    // Update court if extracted value is more specific
                    if (extractedContent.getCourt() != null && !extractedContent.getCourt().isEmpty()) {
                        document.setCourt(extractedContent.getCourt());
                    }
                    
                    logger.info("Content extracted for document {}: Title: '{}', Subject: '{}'", 
                              document.getDocumentId(), extractedContent.getTitle(), extractedContent.getSubject());
                              
                } catch (Exception e) {
                    logger.warn("Failed to extract content from HTML for document {}: {}", 
                              document.getDocumentId(), e.getMessage());
                }
                
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
        // Normalize URL to remove any whitespace characters
        String cleanUrl = url != null ? url.replaceAll("\\s+", "").trim() : url;
        
        if (!url.equals(cleanUrl)) {
            logger.info("URL cleaned: '{}' -> '{}'", url.replace("\n", "\\n").replace("\r", "\\r"), cleanUrl);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(cleanUrl))
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
     * Get storage statistics - adapts to storage mode (file system or Solr)
     */
    public StorageStats getStorageStats() {
        // Check if we're running in Solr mode
        if (environment.acceptsProfiles("solr")) {
            return getSolrStorageStats();
        } else {
            return getFileSystemStorageStats();
        }
    }
    
    /**
     * Get storage statistics from Solr
     */
    private StorageStats getSolrStorageStats() {
        try {
            // Get document count from Solr
            long documentCount = documentRepository.count();
            
            // Estimate size based on average document size (approximation)
            // In a real implementation, you could store file sizes in Solr
            long estimatedAverageSize = 50 * 1024; // 50KB per document (estimated)
            long estimatedTotalSize = documentCount * estimatedAverageSize;
            
            logger.debug("Solr storage stats: {} documents, ~{} MB total", 
                        documentCount, estimatedTotalSize / (1024 * 1024));
            
            return new StorageStats(documentCount, estimatedTotalSize);
            
        } catch (Exception e) {
            logger.error("Failed to get Solr storage stats: {}", e.getMessage());
            return new StorageStats(0, 0L);
        }
    }
    
    /**
     * Get storage statistics from file system (original implementation)
     */
    private StorageStats getFileSystemStorageStats() {
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
            logger.error("Failed to calculate file system storage stats: {}", e.getMessage());
            return new StorageStats(0, 0L);
        }
    }
    
    /**
     * Perform XML validation on downloaded content
     */
    private CompletableFuture<ComprehensiveValidationResult> performXmlValidation(
            String xmlContent, LegalDocument document) {
        
        return xmlValidationService.validateLegalDocument(xmlContent)
                .exceptionally(throwable -> {
                    logger.error("XML validation failed for document {}: {}", 
                               document.getDocumentId(), throwable.getMessage());
                    
                    // Create error result
                    ComprehensiveValidationResult errorResult = new ComprehensiveValidationResult();
                    errorResult.setValid(false);
                    errorResult.addError("Validation process error: " + throwable.getMessage());
                    
                    return errorResult;
                });
    }
    
    /**
     * Process validation results and update document metadata
     */
    private void processValidationResult(LegalDocument document, ComprehensiveValidationResult validationResult) {
        try {
            logger.info("Processing validation result for document {}: {}", 
                       document.getDocumentId(), validationResult.getSummary());
            
            // Update document with ECLI identifier if found
            if (!validationResult.getEcliIdentifiers().isEmpty()) {
                String ecliIdentifier = validationResult.getEcliIdentifiers().iterator().next();
                document.setEcliIdentifier(ecliIdentifier);
                logger.info("Updated document {} with ECLI: {}", document.getDocumentId(), ecliIdentifier);
            }
            
            // Update document type if detected
            if (validationResult.getDocumentType() != null) {
                document.setDocumentType(validationResult.getDocumentType());
                logger.debug("Updated document {} with type: {}", document.getDocumentId(), validationResult.getDocumentType());
            }
            
            // In strict mode, fail the document if validation failed
            if (strictValidationMode && !validationResult.isValid()) {
                document.setStatus(LegalDocument.DocumentStatus.FAILED);
                logger.warn("Document {} failed validation in strict mode", document.getDocumentId());
            } else if (validationResult.isValid()) {
                // Update status to PROCESSED if validation passed
                document.setStatus(LegalDocument.DocumentStatus.PROCESSED);
                logger.info("Document {} passed validation", document.getDocumentId());
            } else {
                // Keep as DOWNLOADED if validation issues but not in strict mode
                logger.warn("Document {} has validation warnings but proceeding: {}", 
                           document.getDocumentId(), validationResult.getWarnings());
            }
            
            // Log validation details for monitoring
            if (!validationResult.getErrors().isEmpty()) {
                logger.warn("Validation errors for document {}: {}", 
                           document.getDocumentId(), validationResult.getErrors());
            }
            
            if (!validationResult.getWarnings().isEmpty()) {
                logger.info("Validation warnings for document {}: {}", 
                           document.getDocumentId(), validationResult.getWarnings());
            }
            
        } catch (Exception e) {
            logger.error("Failed to process validation result for document {}: {}", 
                        document.getDocumentId(), e.getMessage());
        }
    }
    
    /**
     * Download and validate document with enhanced error handling
     */
    public CompletableFuture<ValidationAwareDownloadResult> downloadDocumentWithValidation(LegalDocument document) {
        return CompletableFuture.supplyAsync(() -> {
            ValidationAwareDownloadResult result = new ValidationAwareDownloadResult();
            result.setDocument(document);
            
            try {
                logger.info("Starting enhanced download for document: {}", document.getDocumentId());
                
                // Rate limiting
                Thread.sleep(rateLimitMs);
                
                String xmlContent = fetchDocumentContent(document.getSourceUrl());
                result.setXmlContent(xmlContent);
                
                // Perform validation
                ComprehensiveValidationResult validationResult = 
                    xmlValidationService.validateLegalDocument(xmlContent).get(
                        validationTimeoutSeconds, TimeUnit.SECONDS
                    );
                result.setValidationResult(validationResult);
                
                // Store document
                String filePath = storeDocument(document, xmlContent);
                
                // Process validation results
                processValidationResult(document, validationResult);
                
                // Update document
                document.setFilePath(filePath);
                document.setCrawledAt(LocalDateTime.now());
                
                result.setSuccess(true);
                result.setFilePath(filePath);
                
                logger.info("Enhanced download completed for document: {}", document.getDocumentId());
                
            } catch (Exception e) {
                logger.error("Enhanced download failed for document {}: {}", document.getDocumentId(), e.getMessage());
                document.setStatus(LegalDocument.DocumentStatus.FAILED);
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * Result container for validation-aware downloads
     */
    public static class ValidationAwareDownloadResult {
        private LegalDocument document;
        private String xmlContent;
        private String filePath;
        private ComprehensiveValidationResult validationResult;
        private boolean success;
        private String errorMessage;
        
        // Getters and setters
        public LegalDocument getDocument() { return document; }
        public void setDocument(LegalDocument document) { this.document = document; }
        
        public String getXmlContent() { return xmlContent; }
        public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public ComprehensiveValidationResult getValidationResult() { return validationResult; }
        public void setValidationResult(ComprehensiveValidationResult validationResult) { this.validationResult = validationResult; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public boolean hasValidationErrors() {
            return validationResult != null && !validationResult.getErrors().isEmpty();
        }
        
        public boolean isValidDocument() {
            return success && validationResult != null && validationResult.isValid();
        }
        
        @Override
        public String toString() {
            return String.format("ValidationAwareDownloadResult{document='%s', success=%s, validDocument=%s}",
                               document != null ? document.getDocumentId() : "null", 
                               success, 
                               isValidDocument());
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