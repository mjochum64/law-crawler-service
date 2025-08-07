package de.legal.crawler.service;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.repository.LegalDocumentRepository;
import de.legal.crawler.exception.XmlValidationException;
import de.legal.crawler.service.XmlValidationService.ComprehensiveValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Solr-aware Document Download Service
 * Integrates with both file storage and Solr indexing
 */
@Service
@ConditionalOnProperty(name = "crawler.storage.type", havingValue = "solr")
public class SolrAwareDocumentDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(SolrAwareDocumentDownloadService.class);
    
    @Autowired
    private XmlValidationService xmlValidationService;
    
    @Autowired
    private SolrDocumentService solrDocumentService;
    
    @Autowired
    private LegalDocumentRepository documentRepository;
    
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
    
    @Value("${solr.storage.enable-file-backup:true}")
    private boolean enableFileBackup;
    
    private final HttpClient httpClient;
    
    public SolrAwareDocumentDownloadService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Download and index document in Solr with optional file backup
     */
    public CompletableFuture<SolrIndexResult> downloadAndIndexDocument(LegalDocument document) {
        return CompletableFuture.supplyAsync(() -> {
            SolrIndexResult result = new SolrIndexResult();
            result.setDocument(document);
            
            try {
                logger.info("Starting Solr-aware download for document: {}", document.getDocumentId());
                
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
                
                // Process validation results and enrich document
                processValidationResult(document, validationResult);
                
                // Save to Solr with full-text indexing
                LegalDocument indexedDocument = solrDocumentService.saveWithFullText(document, xmlContent);
                result.setIndexedDocument(indexedDocument);
                
                // Optional file backup
                if (enableFileBackup) {
                    String filePath = storeDocumentBackup(document, xmlContent);
                    document.setFilePath(filePath);
                    result.setBackupFilePath(filePath);
                    logger.debug("Created file backup at: {}", filePath);
                }
                
                // Final status update
                document.setCrawledAt(LocalDateTime.now());
                document.setStatus(validationResult.isValid() && !strictValidationMode 
                    ? LegalDocument.DocumentStatus.PROCESSED 
                    : LegalDocument.DocumentStatus.DOWNLOADED);
                
                result.setSuccess(true);
                
                logger.info("Successfully indexed document in Solr: {}", document.getDocumentId());
                
            } catch (Exception e) {
                logger.error("Solr indexing failed for document {}: {}", document.getDocumentId(), e.getMessage());
                document.setStatus(LegalDocument.DocumentStatus.FAILED);
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * Batch indexing of existing XML files
     */
    public CompletableFuture<BatchIndexResult> indexExistingDocuments() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting batch indexing of existing documents from: {}", basePath);
            
            BatchIndexResult result = new BatchIndexResult();
            
            try {
                Path baseStoragePath = Paths.get(basePath);
                if (!Files.exists(baseStoragePath)) {
                    logger.warn("Base storage path does not exist: {}", basePath);
                    return result;
                }
                
                Files.walk(baseStoragePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".xml"))
                    .forEach(xmlPath -> {
                        try {
                            indexSingleFile(xmlPath, result);
                        } catch (Exception e) {
                            logger.error("Failed to index file: {}", xmlPath, e);
                            result.incrementFailed();
                        }
                    });
                
                logger.info("Batch indexing completed: {} indexed, {} skipped, {} failed", 
                           result.getIndexedCount(), result.getSkippedCount(), result.getFailedCount());
                
            } catch (Exception e) {
                logger.error("Batch indexing failed: {}", e.getMessage());
                result.setError(e.getMessage());
            }
            
            return result;
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
    
    private String storeDocumentBackup(LegalDocument document, String xmlContent) throws IOException {
        // Create structured directory path: court/year/month/
        Path documentPath = createDocumentPath(document);
        
        // Ensure directory exists
        Files.createDirectories(documentPath.getParent());
        
        // Write XML content
        Files.writeString(documentPath, xmlContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        return documentPath.toString();
    }
    
    private Path createDocumentPath(LegalDocument document) {
        // Structure: base-path/court/year/month/documentId.xml
        LocalDateTime decisionDate = document.getDecisionDate() != null 
            ? document.getDecisionDate() 
            : LocalDateTime.now();
        
        String court = document.getCourt() != null 
            ? document.getCourt().toLowerCase() 
            : "unknown";
        String year = String.valueOf(decisionDate.getYear());
        String month = String.format("%02d", decisionDate.getMonthValue());
        String filename = document.getDocumentId() + ".xml";
        
        return Paths.get(basePath, court, year, month, filename);
    }
    
    private void processValidationResult(LegalDocument document, ComprehensiveValidationResult validationResult) {
        try {
            logger.debug("Processing validation result for document {}: {}", 
                       document.getDocumentId(), validationResult.getSummary());
            
            // Update document with ECLI identifier if found
            if (!validationResult.getEcliIdentifiers().isEmpty()) {
                String ecliIdentifier = validationResult.getEcliIdentifiers().iterator().next();
                document.setEcliIdentifier(ecliIdentifier);
                logger.debug("Updated document {} with ECLI: {}", document.getDocumentId(), ecliIdentifier);
            }
            
            // Update document type if detected
            if (validationResult.getDocumentType() != null) {
                document.setDocumentType(validationResult.getDocumentType());
            }
            
        } catch (Exception e) {
            logger.error("Failed to process validation result for document {}: {}", 
                        document.getDocumentId(), e.getMessage());
        }
    }
    
    private void indexSingleFile(Path xmlFile, BatchIndexResult result) throws Exception {
        String xmlContent = Files.readString(xmlFile);
        String documentId = xmlFile.getFileName().toString().replace(".xml", "");
        
        // Check if already indexed in Solr
        if (documentRepository.findByDocumentId(documentId).isPresent()) {
            logger.debug("Document {} already indexed, skipping", documentId);
            result.incrementSkipped();
            return;
        }
        
        // Create document entity
        LegalDocument document = new LegalDocument();
        document.setDocumentId(documentId);
        document.setSourceUrl("file://" + xmlFile.toAbsolutePath());
        document.setFilePath(xmlFile.toString());
        document.setStatus(LegalDocument.DocumentStatus.DOWNLOADED);
        document.setCrawledAt(LocalDateTime.now());
        
        // Extract court from path structure
        String pathStr = xmlFile.toString();
        if (pathStr.contains("/bgh/")) document.setCourt("BGH");
        else if (pathStr.contains("/bag/")) document.setCourt("BAG");
        else if (pathStr.contains("/bsg/")) document.setCourt("BSG");
        else document.setCourt("UNKNOWN");
        
        // Index with full-text
        solrDocumentService.saveWithFullText(document, xmlContent);
        result.incrementIndexed();
        
        logger.debug("Indexed existing document: {}", documentId);
    }
    
    /**
     * Result container for Solr indexing operations
     */
    public static class SolrIndexResult {
        private LegalDocument document;
        private LegalDocument indexedDocument;
        private String xmlContent;
        private String backupFilePath;
        private ComprehensiveValidationResult validationResult;
        private boolean success;
        private String errorMessage;
        
        // Getters and setters
        public LegalDocument getDocument() { return document; }
        public void setDocument(LegalDocument document) { this.document = document; }
        
        public LegalDocument getIndexedDocument() { return indexedDocument; }
        public void setIndexedDocument(LegalDocument indexedDocument) { this.indexedDocument = indexedDocument; }
        
        public String getXmlContent() { return xmlContent; }
        public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }
        
        public String getBackupFilePath() { return backupFilePath; }
        public void setBackupFilePath(String backupFilePath) { this.backupFilePath = backupFilePath; }
        
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
            return String.format("SolrIndexResult{document='%s', success=%s, validDocument=%s, backup=%s}",
                               document != null ? document.getDocumentId() : "null", 
                               success, 
                               isValidDocument(),
                               backupFilePath != null ? "yes" : "no");
        }
    }
    
    /**
     * Batch indexing result
     */
    public static class BatchIndexResult {
        private int indexedCount = 0;
        private int skippedCount = 0;
        private int failedCount = 0;
        private String error;
        
        public void incrementIndexed() { indexedCount++; }
        public void incrementSkipped() { skippedCount++; }
        public void incrementFailed() { failedCount++; }
        
        public int getIndexedCount() { return indexedCount; }
        public int getSkippedCount() { return skippedCount; }
        public int getFailedCount() { return failedCount; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public boolean hasError() { return error != null; }
        
        @Override
        public String toString() {
            return String.format("BatchIndexResult{indexed=%d, skipped=%d, failed=%d, error=%s}",
                               indexedCount, skippedCount, failedCount, error != null ? "yes" : "no");
        }
    }
}