package de.legal.crawler.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for extracting structured content from HTML legal documents
 * downloaded from rechtsprechung-im-internet.de
 */
@Service
public class HtmlContentExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(HtmlContentExtractionService.class);

    public static class ExtractedContent {
        private String title;
        private String fullText;
        private String court;
        private String decisionDate;
        private String caseNumber;
        private String ecli;
        private String documentType;
        private String subject;
        private String norms;
        private Map<String, String> additionalFields = new HashMap<>();

        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getFullText() { return fullText; }
        public void setFullText(String fullText) { this.fullText = fullText; }

        public String getCourt() { return court; }
        public void setCourt(String court) { this.court = court; }

        public String getDecisionDate() { return decisionDate; }
        public void setDecisionDate(String decisionDate) { this.decisionDate = decisionDate; }

        public String getCaseNumber() { return caseNumber; }
        public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }

        public String getEcli() { return ecli; }
        public void setEcli(String ecli) { this.ecli = ecli; }

        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getNorms() { return norms; }
        public void setNorms(String norms) { this.norms = norms; }

        public Map<String, String> getAdditionalFields() { return additionalFields; }
        public void setAdditionalFields(Map<String, String> additionalFields) { this.additionalFields = additionalFields; }
    }

    /**
     * Extract structured content from HTML document
     */
    public ExtractedContent extractContent(String htmlContent) {
        ExtractedContent content = new ExtractedContent();
        
        try {
            Document doc = Jsoup.parse(htmlContent);
            
            // Extract title from page title
            Element titleElement = doc.selectFirst("title");
            if (titleElement != null) {
                content.setTitle(cleanText(titleElement.text()));
            }
            
            // Extract structured metadata from document header table
            extractDocumentMetadata(doc, content);
            
            // Extract main content/subject
            extractMainContent(doc, content);
            
            // Extract full text for search
            extractFullTextContent(doc, content);
            
            logger.debug("Successfully extracted content from HTML document. Title: {}", content.getTitle());
            
        } catch (Exception e) {
            logger.error("Failed to extract content from HTML: {}", e.getMessage(), e);
        }
        
        return content;
    }

    private void extractDocumentMetadata(Document doc, ExtractedContent content) {
        // Extract from structured table with TD30/TD70 classes
        Elements metadataRows = doc.select("table td.TD30");
        
        for (Element labelCell : metadataRows) {
            String label = cleanText(labelCell.text());
            Element valueCell = labelCell.parent().selectFirst("td.TD70, td.TD70BREAK");
            
            if (valueCell != null) {
                String value = cleanText(valueCell.text());
                
                switch (label.toLowerCase()) {
                    case "gericht:":
                        content.setCourt(value);
                        break;
                    case "entscheidungsdatum:":
                        content.setDecisionDate(value);
                        break;
                    case "aktenzeichen:":
                        content.setCaseNumber(value);
                        break;
                    case "ecli:":
                        content.setEcli(value);
                        break;
                    case "dokumenttyp:":
                        content.setDocumentType(value);
                        break;
                    case "normen:":
                        content.setNorms(value);
                        break;
                    default:
                        if (!value.isEmpty()) {
                            content.getAdditionalFields().put(label.replace(":", ""), value);
                        }
                        break;
                }
            }
        }
    }

    private void extractMainContent(Document doc, ExtractedContent content) {
        // Extract subject/title from document layout
        Element subjectElement = doc.selectFirst(".docLayoutTitel p, .RspDL dd p");
        if (subjectElement != null) {
            content.setSubject(cleanText(subjectElement.text()));
        }
    }

    private void extractFullTextContent(Document doc, ExtractedContent content) {
        StringBuilder fullText = new StringBuilder();
        
        // Add title
        if (content.getTitle() != null) {
            fullText.append(content.getTitle()).append("\n\n");
        }
        
        // Add subject
        if (content.getSubject() != null) {
            fullText.append(content.getSubject()).append("\n\n");
        }
        
        // Add all text content from main document areas
        Elements contentElements = doc.select(".docLayoutText, .docLayoutTitel, .RspDL");
        for (Element element : contentElements) {
            String text = cleanText(element.text());
            if (!text.isEmpty() && text.length() > 10) { // Filter out very short texts
                fullText.append(text).append("\n");
            }
        }
        
        // Add metadata as searchable text
        fullText.append("\nGericht: ").append(content.getCourt() != null ? content.getCourt() : "").append("\n");
        fullText.append("Aktenzeichen: ").append(content.getCaseNumber() != null ? content.getCaseNumber() : "").append("\n");
        fullText.append("Dokumenttyp: ").append(content.getDocumentType() != null ? content.getDocumentType() : "").append("\n");
        
        if (content.getNorms() != null && !content.getNorms().isEmpty()) {
            fullText.append("Normen: ").append(content.getNorms()).append("\n");
        }
        
        content.setFullText(fullText.toString().trim());
    }

    private String cleanText(String text) {
        if (text == null) return "";
        
        return text.replaceAll("\\s+", " ")  // Replace multiple whitespace with single space
                   .replaceAll("\\|", " ")   // Replace pipe symbols
                   .replaceAll("&#x[0-9A-Fa-f]+;", " ") // Remove HTML entities
                   .trim();
    }
}