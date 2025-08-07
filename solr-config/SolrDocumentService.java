package de.legal.crawler.service;

import de.legal.crawler.model.LegalDocument;
import de.legal.crawler.repository.SolrDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enhanced service for Solr-based document management
 * Provides indexing with full-text content extraction
 */
@Service
@ConditionalOnProperty(name = "crawler.storage.type", havingValue = "solr")
public class SolrDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(SolrDocumentService.class);

    @Autowired
    private SolrDocumentRepository repository;

    @Autowired
    private XmlValidationService xmlValidationService;

    /**
     * Save document with full-text indexing
     */
    public LegalDocument saveWithFullText(LegalDocument document, String xmlContent) {
        try {
            // Extract structured content from XML
            extractContentFromXml(document, xmlContent);
            
            // Save to Solr (includes full-text indexing)
            LegalDocument saved = repository.save(document);
            
            logger.info("Document {} indexed in Solr with full-text content", 
                       document.getDocumentId());
            return saved;
            
        } catch (Exception e) {
            logger.error("Failed to save document with full-text: {}", 
                        document.getDocumentId(), e);
            throw new RuntimeException("Solr indexing failed", e);
        }
    }

    /**
     * Full-text search with highlighting
     */
    public List<LegalDocument> search(String searchTerm) {
        return repository.fullTextSearch(searchTerm, 50);
    }

    /**
     * Find similar documents using More Like This
     */
    public List<LegalDocument> findSimilarDocuments(String documentId) {
        // Implementation would use Solr's MLT handler
        logger.info("Finding similar documents to: {}", documentId);
        return repository.findAllWithQuery("id:" + documentId).subList(0, 10);
    }

    /**
     * Get autocomplete suggestions
     */
    public List<String> getSuggestions(String term) {
        return repository.getSuggestions(term);
    }

    /**
     * Extract structured content from XML for better indexing
     */
    private void extractContentFromXml(LegalDocument document, String xmlContent) {
        // Parse HTML/XML content to extract structured fields
        try {
            // Extract title from HTML title tag
            if (xmlContent.contains("<title>")) {
                String title = extractBetween(xmlContent, "<title>", "</title>");
                if (title != null && !title.trim().isEmpty()) {
                    document.setTitle(cleanHtmlEntities(title));
                }
            }

            // Extract court information
            String court = extractTableValue(xmlContent, "Gericht:");
            if (court != null) {
                document.setCourt(extractCourtCode(court));
            }

            // Extract ECLI
            String ecli = extractTableValue(xmlContent, "ECLI:");
            if (ecli != null) {
                document.setEcliIdentifier(ecli);
            }

            // Extract case number  
            String caseNumber = extractTableValue(xmlContent, "Aktenzeichen:");
            document.setCaseNumber(caseNumber);

            // Extract document type
            String docType = extractTableValue(xmlContent, "Dokumenttyp:");
            document.setDocumentType(docType);

            // Extract decision date
            String dateStr = extractTableValue(xmlContent, "Entscheidungsdatum:");
            if (dateStr != null) {
                // Parse German date format dd.MM.yyyy
                try {
                    String[] parts = dateStr.split("\\.");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[2]);
                        document.setDecisionDate(LocalDateTime.of(year, month, day, 0, 0));
                    }
                } catch (Exception e) {
                    logger.warn("Could not parse date: {}", dateStr);
                }
            }

            // Extract full text content (remove HTML tags)
            String fullText = extractFullText(xmlContent);
            document.setFullText(fullText);

            // Extract Leitsatz
            String leitsatz = extractSectionContent(xmlContent, "Leitsatz");
            document.setLeitsatz(leitsatz);

            // Extract Tenor
            String tenor = extractSectionContent(xmlContent, "Tenor");
            document.setTenor(tenor);

            // Extract Gründe
            String gruende = extractSectionContent(xmlContent, "Gründe");
            document.setGruende(gruende);

        } catch (Exception e) {
            logger.warn("Failed to extract structured content for document: {}", 
                       document.getDocumentId(), e);
        }
    }

    private String extractTableValue(String html, String fieldName) {
        String pattern = "<strong>" + fieldName + "</strong></td><td[^>]*>([^<]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return cleanHtmlEntities(m.group(1).trim());
        }
        return null;
    }

    private String extractBetween(String text, String start, String end) {
        int startIdx = text.indexOf(start);
        if (startIdx == -1) return null;
        startIdx += start.length();
        
        int endIdx = text.indexOf(end, startIdx);
        if (endIdx == -1) return null;
        
        return text.substring(startIdx, endIdx);
    }

    private String extractSectionContent(String html, String sectionName) {
        String pattern = "<h[34][^>]*>" + sectionName + "</h[34]>.*?<div[^>]*>(.*?)</div>";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, 
            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return cleanHtml(m.group(1));
        }
        return null;
    }

    private String extractFullText(String html) {
        // Remove HTML tags and extract readable text
        String text = html.replaceAll("<[^>]+>", " ");
        text = cleanHtmlEntities(text);
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 50000 ? text.substring(0, 50000) + "..." : text;
    }

    private String extractCourtCode(String courtName) {
        if (courtName.contains("BGH")) return "BGH";
        if (courtName.contains("BVerfG")) return "BVerfG";
        if (courtName.contains("BAG")) return "BAG";
        if (courtName.contains("BSG")) return "BSG";
        if (courtName.contains("BVerwG")) return "BVerwG";
        if (courtName.contains("BFH")) return "BFH";
        if (courtName.contains("BPatG")) return "BPatG";
        return "UNKNOWN";
    }

    private String cleanHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String cleanHtmlEntities(String text) {
        return text.replace("&amp;", "&")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&#x7c;", "|")
                  .replace("&#x2f;", "/")
                  .replace("&auml;", "ä")
                  .replace("&ouml;", "ö")
                  .replace("&uuml;", "ü")
                  .replace("&Auml;", "Ä")
                  .replace("&Ouml;", "Ö")
                  .replace("&Uuml;", "Ü")
                  .replace("&szlig;", "ß");
    }

    /**
     * Bulk index existing XML files
     */
    public void indexExistingDocuments(String documentsPath) {
        try {
            Path basePath = Paths.get(documentsPath);
            Files.walk(basePath)
                .filter(path -> path.toString().endsWith(".xml"))
                .forEach(this::indexSingleFile);
                
        } catch (IOException e) {
            logger.error("Failed to index existing documents from: {}", documentsPath, e);
        }
    }

    private void indexSingleFile(Path xmlFile) {
        try {
            String xmlContent = Files.readString(xmlFile);
            String documentId = xmlFile.getFileName().toString().replace(".xml", "");
            
            LegalDocument document = new LegalDocument();
            document.setDocumentId(documentId);
            document.setSourceUrl("file://" + xmlFile.toAbsolutePath());
            document.setFilePath(xmlFile.toString());
            document.setStatus(LegalDocument.DocumentStatus.DOWNLOADED);
            document.setCrawledAt(LocalDateTime.now());
            
            saveWithFullText(document, xmlContent);
            
            logger.debug("Indexed existing document: {}", documentId);
            
        } catch (Exception e) {
            logger.error("Failed to index file: {}", xmlFile, e);
        }
    }
}