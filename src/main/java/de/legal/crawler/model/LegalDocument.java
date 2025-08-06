package de.legal.crawler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Entity representing a legal document from rechtsprechung-im-internet.de
 */
@Entity
@Table(name = "legal_documents")
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String documentId; // KARE, KORE, KSRE, WBRE IDs

    @NotBlank
    @Column(nullable = false)
    private String court; // BAG, BGH, BSG, BVerwG

    @Column
    private String ecliIdentifier; // European Case Law Identifier

    @NotNull
    @Column(nullable = false)
    private String sourceUrl;

    @Column
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime decisionDate;

    @Column
    private LocalDateTime crawledAt;

    @Column
    private String filePath; // Local storage path for XML content

    @NotNull
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column
    private String documentType; // Decision, Order, etc.

    // Constructors
    public LegalDocument() {}

    public LegalDocument(String documentId, String court, String sourceUrl, LocalDateTime decisionDate) {
        this.documentId = documentId;
        this.court = court;
        this.sourceUrl = sourceUrl;
        this.decisionDate = decisionDate;
        this.crawledAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getCourt() { return court; }
    public void setCourt(String court) { this.court = court; }

    public String getEcliIdentifier() { return ecliIdentifier; }
    public void setEcliIdentifier(String ecliIdentifier) { this.ecliIdentifier = ecliIdentifier; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getDecisionDate() { return decisionDate; }
    public void setDecisionDate(LocalDateTime decisionDate) { this.decisionDate = decisionDate; }

    public LocalDateTime getCrawledAt() { return crawledAt; }
    public void setCrawledAt(LocalDateTime crawledAt) { this.crawledAt = crawledAt; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public enum DocumentStatus {
        PENDING, DOWNLOADED, PROCESSED, FAILED
    }
}