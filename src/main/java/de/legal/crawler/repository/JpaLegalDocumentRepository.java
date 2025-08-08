package de.legal.crawler.repository;

import de.legal.crawler.model.LegalDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository interface for LegalDocument entities (H2 database mode)
 */
@Repository("jpaLegalDocumentRepository")
@org.springframework.context.annotation.Profile("!solr")
public interface JpaLegalDocumentRepository extends JpaRepository<LegalDocument, Long>, LegalDocumentRepository {

    /**
     * Find document by unique document ID
     */
    Optional<LegalDocument> findByDocumentId(String documentId);
    
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
    @Query("SELECT d FROM LegalDocument d WHERE d.decisionDate BETWEEN :startDate AND :endDate ORDER BY d.decisionDate DESC")
    List<LegalDocument> findByDecisionDateBetween(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate);
    
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
    @Query("SELECT d.court, COUNT(d) FROM LegalDocument d GROUP BY d.court")
    List<Object[]> countDocumentsByCourt();
    
    /**
     * Count documents by status
     */
    @Query("SELECT d.status, COUNT(d) FROM LegalDocument d GROUP BY d.status")
    List<Object[]> countDocumentsByStatus();
    
    /**
     * Find failed documents for retry
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.status = 'FAILED' AND d.crawledAt < :retryAfter")
    List<LegalDocument> findFailedDocumentsForRetry(@Param("retryAfter") LocalDateTime retryAfter);
    
    /**
     * Check if document exists by URL
     */
    boolean existsBySourceUrl(String sourceUrl);
    
    /**
     * Find documents by court with pagination
     */
    Page<LegalDocument> findByCourtOrderByDecisionDateDesc(String court, Pageable pageable);
    
    /**
     * Search documents by title containing text
     */
    @Query("SELECT d FROM LegalDocument d WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalDocument> findByTitleContainingIgnoreCase(@Param("searchTerm") String searchTerm);
    
    /**
     * Find recent documents (last N days)
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.decisionDate >= :since ORDER BY d.decisionDate DESC")
    List<LegalDocument> findRecentDocuments(@Param("since") LocalDateTime since);
    
    /**
     * Find documents with custom query and sorting (JPA implementation limited)
     */
    default List<LegalDocument> findAllWithQueryAndSort(String query, String sort) {
        // For JPA repository, provide basic implementation
        // Full-text search is better handled by Solr
        return List.of();
    }
}