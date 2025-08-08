package de.legal.crawler.repository;

import de.legal.crawler.model.LegalDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Common interface for LegalDocument repository implementations
 * Implemented by both JPA and Solr repositories
 */
public interface LegalDocumentRepository {

    /**
     * Save a single document
     */
    <S extends LegalDocument> S save(S document);
    
    /**
     * Save multiple documents
     */
    <S extends LegalDocument> Iterable<S> saveAll(Iterable<S> documents);
    
    /**
     * Find document by ID
     */
    Optional<LegalDocument> findById(Long id);
    
    /**
     * Find document by unique document ID
     */
    Optional<LegalDocument> findByDocumentId(String documentId);
    
    /**
     * Check if document exists by ID
     */
    boolean existsById(Long id);
    
    /**
     * Check if document exists by URL
     */
    boolean existsBySourceUrl(String sourceUrl);
    
    /**
     * Find all documents
     */
    Iterable<LegalDocument> findAll();
    
    /**
     * Find documents by IDs
     */
    Iterable<LegalDocument> findAllById(Iterable<Long> ids);
    
    /**
     * Count all documents
     */
    long count();
    
    /**
     * Delete document by ID
     */
    void deleteById(Long id);
    
    /**
     * Delete document
     */
    void delete(LegalDocument document);
    
    /**
     * Delete documents by IDs
     */
    void deleteAllById(Iterable<? extends Long> ids);
    
    /**
     * Delete documents
     */
    void deleteAll(Iterable<? extends LegalDocument> documents);
    
    /**
     * Delete all documents
     */
    void deleteAll();

    // Custom query methods
    
    /**
     * Find all documents by court
     */
    List<LegalDocument> findByCourtOrderByDecisionDateDesc(String court);
    
    /**
     * Find documents by status
     */
    List<LegalDocument> findByStatus(LegalDocument.DocumentStatus status);
    
    /**
     * Find documents by court and status
     */
    List<LegalDocument> findByCourtAndStatus(String court, LegalDocument.DocumentStatus status);
    
    /**
     * Find documents within date range
     */
    List<LegalDocument> findByDecisionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find documents crawled after a specific date
     */
    List<LegalDocument> findByCrawledAtAfterOrderByCrawledAtDesc(LocalDateTime crawledAfter);
    
    /**
     * Find documents by ECLI identifier
     */
    Optional<LegalDocument> findByEcliIdentifier(String ecliIdentifier);
    
    /**
     * Count documents by court
     */
    List<Object[]> countDocumentsByCourt();
    
    /**
     * Count documents by status
     */
    List<Object[]> countDocumentsByStatus();
    
    /**
     * Find failed documents for retry
     */
    List<LegalDocument> findFailedDocumentsForRetry(LocalDateTime retryAfter);
    
    /**
     * Find documents by court with pagination
     */
    Page<LegalDocument> findByCourtOrderByDecisionDateDesc(String court, Pageable pageable);
    
    /**
     * Search documents by title containing text
     */
    List<LegalDocument> findByTitleContainingIgnoreCase(String searchTerm);
    
    /**
     * Find recent documents
     */
    List<LegalDocument> findRecentDocuments(LocalDateTime since);
    
    /**
     * Find documents with custom query and sorting
     */
    List<LegalDocument> findAllWithQueryAndSort(String query, String sort);
}