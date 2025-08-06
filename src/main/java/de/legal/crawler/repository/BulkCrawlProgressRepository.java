package de.legal.crawler.repository;

import de.legal.crawler.model.BulkCrawlProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for bulk crawl progress tracking
 */
@Repository
public interface BulkCrawlProgressRepository extends JpaRepository<BulkCrawlProgress, Long> {

    /**
     * Find bulk crawl operation by operation ID
     */
    Optional<BulkCrawlProgress> findByOperationId(String operationId);

    /**
     * Find all active (running) bulk crawl operations
     */
    @Query("SELECT b FROM BulkCrawlProgress b WHERE b.status IN ('INITIALIZING', 'DISCOVERING', 'CRAWLING', 'RESUMING')")
    List<BulkCrawlProgress> findActiveOperations();

    /**
     * Find all paused bulk crawl operations
     */
    @Query("SELECT b FROM BulkCrawlProgress b WHERE b.status = 'PAUSED'")
    List<BulkCrawlProgress> findPausedOperations();

    /**
     * Find completed operations within a time range
     */
    @Query("SELECT b FROM BulkCrawlProgress b WHERE b.status = 'COMPLETED' AND b.completedAt BETWEEN :startTime AND :endTime")
    List<BulkCrawlProgress> findCompletedOperations(@Param("startTime") LocalDateTime startTime, 
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * Find operations that failed and might need retry
     */
    @Query("SELECT b FROM BulkCrawlProgress b WHERE b.status = 'FAILED' AND b.retryCount < 3 AND b.createdAt > :afterTime")
    List<BulkCrawlProgress> findFailedOperationsForRetry(@Param("afterTime") LocalDateTime afterTime);

    /**
     * Find operations by status
     */
    List<BulkCrawlProgress> findByStatus(BulkCrawlProgress.BulkCrawlStatus status);

    /**
     * Find all operations ordered by creation time
     */
    List<BulkCrawlProgress> findAllByOrderByCreatedAtDesc();

    /**
     * Find recent operations (within specified days)
     */
    @Query("SELECT b FROM BulkCrawlProgress b WHERE b.createdAt > :afterTime ORDER BY b.createdAt DESC")
    List<BulkCrawlProgress> findRecentOperations(@Param("afterTime") LocalDateTime afterTime);

    /**
     * Count operations by status
     */
    long countByStatus(BulkCrawlProgress.BulkCrawlStatus status);

    /**
     * Get operations statistics
     */
    @Query("SELECT " +
           "COUNT(b) as totalOps, " +
           "SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedOps, " +
           "SUM(CASE WHEN b.status = 'FAILED' THEN 1 ELSE 0 END) as failedOps, " +
           "SUM(CASE WHEN b.status IN ('INITIALIZING', 'DISCOVERING', 'CRAWLING', 'RESUMING') THEN 1 ELSE 0 END) as activeOps, " +
           "SUM(b.documentsSucceeded) as totalDocsSucceeded, " +
           "SUM(b.documentsFailed) as totalDocsFailed " +
           "FROM BulkCrawlProgress b")
    Object[] getOperationsStatistics();

    /**
     * Delete old completed operations
     */
    @Query("DELETE FROM BulkCrawlProgress b WHERE b.status IN ('COMPLETED', 'CANCELLED') AND b.completedAt < :beforeTime")
    void deleteOldCompletedOperations(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * Find operations that are stuck (running for too long)
     */
    @Query("SELECT b FROM BulkCrawlProgress b WHERE b.status IN ('DISCOVERING', 'CRAWLING') AND b.startedAt < :stuckThreshold")
    List<BulkCrawlProgress> findStuckOperations(@Param("stuckThreshold") LocalDateTime stuckThreshold);
}