package de.legal.crawler.service;

import de.legal.crawler.model.BulkCrawlProgress;
import de.legal.crawler.repository.BulkCrawlProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for orchestrating large-scale bulk crawling operations
 * Handles discovery, progress tracking, pause/resume, and error recovery
 */
@Service
public class BulkCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(BulkCrawlerService.class);
    
    @Autowired
    private SitemapDiscoveryService discoveryService;
    
    @Autowired
    private CrawlerOrchestrationService orchestrationService;
    
    @Autowired
    private BulkCrawlProgressRepository progressRepository;
    
    @Value("${crawler.bulk.max-concurrent-operations:2}")
    private int maxConcurrentOperations;
    
    @Value("${crawler.bulk.default-rate-limit-ms:2000}")
    private long defaultRateLimitMs;
    
    @Value("${crawler.bulk.default-max-concurrent-downloads:5}")
    private int defaultMaxConcurrentDownloads;
    
    @Value("${crawler.bulk.progress-update-interval-ms:30000}")
    private long progressUpdateIntervalMs;
    
    @Value("${crawler.bulk.stuck-operation-timeout-hours:6}")
    private int stuckOperationTimeoutHours;
    
    // Track active operations to prevent concurrent execution issues
    private final ConcurrentHashMap<String, AtomicBoolean> activeOperations = new ConcurrentHashMap<>();
    
    /**
     * Start a full bulk crawl operation for all available documents
     */
    @Async
    public CompletableFuture<String> startFullBulkCrawl(BulkCrawlConfiguration config) {
        String operationId = generateOperationId();
        logger.info("Starting full bulk crawl operation: {}", operationId);
        
        try {
            // Check if we've exceeded max concurrent operations
            if (activeOperations.size() >= maxConcurrentOperations) {
                throw new IllegalStateException("Maximum concurrent bulk operations limit reached");
            }
            
            // Create progress tracking entity
            BulkCrawlProgress progress = new BulkCrawlProgress(operationId, null, null);
            progress.setForceUpdate(config.isForceUpdate());
            progress.setRateLimitMs(config.getRateLimitMs());
            progress.setMaxConcurrentDownloads(config.getMaxConcurrentDownloads());
            progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.INITIALIZING);
            progress.setCurrentPhase("INITIALIZATION");
            
            progress = progressRepository.save(progress);
            
            // Register active operation
            activeOperations.put(operationId, new AtomicBoolean(false));
            
            // Start the bulk crawl process asynchronously
            executeBulkCrawl(progress);
            
            return CompletableFuture.completedFuture(operationId);
            
        } catch (Exception e) {
            logger.error("Failed to start bulk crawl operation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start bulk crawl", e);
        }
    }
    
    /**
     * Start a bulk crawl operation for a specific date range
     */
    @Async
    @Transactional
    public CompletableFuture<String> startDateRangeBulkCrawl(LocalDate startDate, LocalDate endDate, 
                                                           BulkCrawlConfiguration config) {
        String operationId = generateOperationId();
        logger.info("Starting date range bulk crawl operation: {} ({} to {})", operationId, startDate, endDate);
        
        try {
            if (activeOperations.size() >= maxConcurrentOperations) {
                throw new IllegalStateException("Maximum concurrent bulk operations limit reached");
            }
            
            BulkCrawlProgress progress = new BulkCrawlProgress(operationId, startDate, endDate);
            progress.setForceUpdate(config.isForceUpdate());
            progress.setRateLimitMs(config.getRateLimitMs());
            progress.setMaxConcurrentDownloads(config.getMaxConcurrentDownloads());
            progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.INITIALIZING);
            progress.setCurrentPhase("INITIALIZATION");
            
            progress = progressRepository.save(progress);
            
            activeOperations.put(operationId, new AtomicBoolean(false));
            
            executeBulkCrawl(progress);
            
            return CompletableFuture.completedFuture(operationId);
            
        } catch (Exception e) {
            logger.error("Failed to start date range bulk crawl operation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start bulk crawl", e);
        }
    }
    
    /**
     * Pause a running bulk crawl operation
     */
    @Transactional
    public boolean pauseBulkCrawl(String operationId) {
        logger.info("Pausing bulk crawl operation: {}", operationId);
        
        Optional<BulkCrawlProgress> progressOpt = progressRepository.findByOperationId(operationId);
        if (progressOpt.isEmpty()) {
            return false;
        }
        
        BulkCrawlProgress progress = progressOpt.get();
        if (!progress.isRunning()) {
            logger.warn("Cannot pause operation {} - not in running state: {}", operationId, progress.getStatus());
            return false;
        }
        
        progress.setPauseRequested(true);
        progressRepository.save(progress);
        
        return true;
    }
    
    /**
     * Resume a paused bulk crawl operation
     */
    @Async
    @Transactional
    public CompletableFuture<Boolean> resumeBulkCrawl(String operationId) {
        logger.info("Resuming bulk crawl operation: {}", operationId);
        
        Optional<BulkCrawlProgress> progressOpt = progressRepository.findByOperationId(operationId);
        if (progressOpt.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        
        BulkCrawlProgress progress = progressOpt.get();
        if (!progress.isPaused()) {
            logger.warn("Cannot resume operation {} - not in paused state: {}", operationId, progress.getStatus());
            return CompletableFuture.completedFuture(false);
        }
        
        progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.RESUMING);
        progress.setPauseRequested(false);
        progress.setPausedAt(null);
        progressRepository.save(progress);
        
        // Re-register and continue execution
        activeOperations.put(operationId, new AtomicBoolean(false));
        executeBulkCrawl(progress);
        
        return CompletableFuture.completedFuture(true);
    }
    
    /**
     * Cancel a bulk crawl operation
     */
    @Transactional
    public boolean cancelBulkCrawl(String operationId) {
        logger.info("Cancelling bulk crawl operation: {}", operationId);
        
        Optional<BulkCrawlProgress> progressOpt = progressRepository.findByOperationId(operationId);
        if (progressOpt.isEmpty()) {
            return false;
        }
        
        BulkCrawlProgress progress = progressOpt.get();
        if (progress.isCompleted()) {
            logger.warn("Cannot cancel operation {} - already completed: {}", operationId, progress.getStatus());
            return false;
        }
        
        progress.setCancelRequested(true);
        progressRepository.save(progress);
        
        return true;
    }
    
    /**
     * Get progress information for a bulk crawl operation
     */
    public Optional<BulkCrawlProgress> getBulkCrawlProgress(String operationId) {
        return progressRepository.findByOperationId(operationId);
    }
    
    /**
     * Get all active bulk crawl operations
     */
    public List<BulkCrawlProgress> getActiveOperations() {
        return progressRepository.findActiveOperations();
    }
    
    /**
     * Get recent bulk crawl operations
     */
    public List<BulkCrawlProgress> getRecentOperations(int daysBack) {
        LocalDateTime afterTime = LocalDateTime.now().minusDays(daysBack);
        return progressRepository.findRecentOperations(afterTime);
    }
    
    /**
     * Get bulk crawl operations statistics
     */
    public BulkCrawlStatistics getStatistics() {
        Object[] stats = progressRepository.getOperationsStatistics();
        return new BulkCrawlStatistics(stats);
    }
    
    /**
     * Clean up old completed operations
     */
    @Transactional
    public int cleanupOldOperations(int daysToKeep) {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(daysToKeep);
        List<BulkCrawlProgress> toDelete = progressRepository.findCompletedOperations(beforeTime, LocalDateTime.now());
        int count = toDelete.size();
        
        progressRepository.deleteOldCompletedOperations(beforeTime);
        logger.info("Cleaned up {} old bulk crawl operations", count);
        
        return count;
    }
    
    /**
     * Handle stuck operations (running for too long)
     */
    @Transactional
    public int handleStuckOperations() {
        LocalDateTime stuckThreshold = LocalDateTime.now().minusHours(stuckOperationTimeoutHours);
        List<BulkCrawlProgress> stuckOps = progressRepository.findStuckOperations(stuckThreshold);
        
        for (BulkCrawlProgress progress : stuckOps) {
            logger.warn("Found stuck operation: {} - marking as failed", progress.getOperationId());
            progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.FAILED);
            progress.setErrorMessage("Operation timed out - stuck for too long");
            progress.setCompletedAt(LocalDateTime.now());
            progressRepository.save(progress);
            
            // Remove from active operations
            activeOperations.remove(progress.getOperationId());
        }
        
        return stuckOps.size();
    }
    
    private void executeBulkCrawl(BulkCrawlProgress progress) {
        CompletableFuture.runAsync(() -> {
            String operationId = progress.getOperationId();
            AtomicBoolean cancelFlag = activeOperations.get(operationId);
            
            try {
                logger.info("Starting bulk crawl execution for operation: {}", operationId);
                
                // Phase 1: Discovery
                progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.DISCOVERING);
                progress.setCurrentPhase("DISCOVERY");
                progress.setStartedAt(LocalDateTime.now());
                progressRepository.save(progress);
                
                long discoveryStart = System.currentTimeMillis();
                SitemapDiscoveryService.SitemapDiscoveryResult discoveryResult;
                
                if (progress.getStartDate() != null && progress.getEndDate() != null) {
                    // Date range discovery
                    discoveryResult = discoveryService.discoverAvailableSitemaps(
                        progress.getStartDate(), progress.getEndDate()).get();
                } else {
                    // Full range discovery
                    discoveryResult = discoveryService.discoverFullRange().get();
                }
                
                progress.setDiscoveryTimeMs(System.currentTimeMillis() - discoveryStart);
                progress.setTotalDatesDiscovered(discoveryResult.getAvailableCount());
                progress.setStartDate(discoveryResult.getEarliestDate());
                progress.setEndDate(discoveryResult.getLatestDate());
                
                logger.info("Discovery completed for {}: {} dates found", operationId, discoveryResult.getAvailableCount());
                
                // Check for cancellation
                if (checkCancellation(progress, cancelFlag)) return;
                
                // Phase 2: Crawling
                progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.CRAWLING);
                progress.setCurrentPhase("CRAWLING");
                progressRepository.save(progress);
                
                long crawlStart = System.currentTimeMillis();
                int successfulDates = 0;
                int failedDates = 0;
                
                for (LocalDate date : discoveryResult.getAvailableDates()) {
                    // Check for pause/cancel
                    if (checkPauseOrCancellation(progress, cancelFlag)) return;
                    
                    try {
                        progress.setCurrentProcessingDate(date);
                        progressRepository.save(progress);
                        
                        // Crawl single date
                        CrawlerOrchestrationService.CrawlResult result = 
                            orchestrationService.startCrawling(date, progress.isForceUpdate()).get();
                        
                        // Update progress
                        progress.addProcessedDate(date);
                        progress.incrementProcessedDates();
                        progress.setDocumentsSucceeded(progress.getDocumentsSucceeded() + result.getTotalProcessed());
                        progress.setDocumentsFailed(progress.getDocumentsFailed() + result.getFailedDocuments());
                        progress.setDocumentsProcessed(progress.getDocumentsSucceeded() + progress.getDocumentsFailed());
                        
                        progress.updateProcessingRate();
                        progress.updateEstimatedCompletion();
                        
                        successfulDates++;
                        
                        logger.info("Completed crawling for date {}: {} docs processed", 
                                  date, result.getTotalProcessed());
                        
                    } catch (Exception e) {
                        logger.error("Failed to crawl date {}: {}", date, e.getMessage());
                        progress.addFailedDate(date);
                        failedDates++;
                    }
                    
                    // Save progress periodically
                    if ((successfulDates + failedDates) % 10 == 0) {
                        progressRepository.save(progress);
                    }
                    
                    // Rate limiting between dates
                    Thread.sleep(progress.getRateLimitMs());
                }
                
                // Phase 3: Completion
                progress.setDownloadTimeMs(System.currentTimeMillis() - crawlStart);
                progress.setTotalProcessingTimeMs(System.currentTimeMillis() - 
                    progress.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.COMPLETED);
                progress.setCurrentPhase("COMPLETED");
                progress.setCompletedAt(LocalDateTime.now());
                
                progressRepository.save(progress);
                
                logger.info("Bulk crawl operation {} completed successfully: {} dates processed, {} succeeded, {} failed", 
                          operationId, progress.getDatesProcessed(), successfulDates, failedDates);
                
            } catch (Exception e) {
                logger.error("Bulk crawl operation {} failed: {}", operationId, e.getMessage(), e);
                
                progress.setStatus(BulkCrawlProgress.BulkCrawlStatus.FAILED);
                progress.setErrorMessage(e.getMessage());
                progress.setCompletedAt(LocalDateTime.now());
                progressRepository.save(progress);
                
            } finally {
                // Cleanup
                activeOperations.remove(operationId);
            }
        });
    }
    
    private boolean checkCancellation(BulkCrawlProgress progress, AtomicBoolean cancelFlag) {
        BulkCrawlProgress current = progressRepository.findByOperationId(progress.getOperationId()).orElse(progress);
        
        if (current.isCancelRequested() || cancelFlag.get()) {
            current.setStatus(BulkCrawlProgress.BulkCrawlStatus.CANCELLED);
            current.setCompletedAt(LocalDateTime.now());
            progressRepository.save(current);
            logger.info("Bulk crawl operation {} was cancelled", progress.getOperationId());
            return true;
        }
        return false;
    }
    
    private boolean checkPauseOrCancellation(BulkCrawlProgress progress, AtomicBoolean cancelFlag) {
        BulkCrawlProgress current = progressRepository.findByOperationId(progress.getOperationId()).orElse(progress);
        
        if (current.isCancelRequested() || cancelFlag.get()) {
            return checkCancellation(current, cancelFlag);
        }
        
        if (current.isPauseRequested()) {
            current.setStatus(BulkCrawlProgress.BulkCrawlStatus.PAUSED);
            current.setPausedAt(LocalDateTime.now());
            progressRepository.save(current);
            logger.info("Bulk crawl operation {} was paused", progress.getOperationId());
            return true;
        }
        
        return false;
    }
    
    private String generateOperationId() {
        return "bulk-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Configuration for bulk crawl operations
     */
    public static class BulkCrawlConfiguration {
        private boolean forceUpdate = false;
        private long rateLimitMs = 2000;
        private int maxConcurrentDownloads = 5;
        
        public BulkCrawlConfiguration() {}
        
        public BulkCrawlConfiguration(boolean forceUpdate, long rateLimitMs, int maxConcurrentDownloads) {
            this.forceUpdate = forceUpdate;
            this.rateLimitMs = rateLimitMs;
            this.maxConcurrentDownloads = maxConcurrentDownloads;
        }
        
        // Getters and setters
        public boolean isForceUpdate() { return forceUpdate; }
        public void setForceUpdate(boolean forceUpdate) { this.forceUpdate = forceUpdate; }
        
        public long getRateLimitMs() { return rateLimitMs; }
        public void setRateLimitMs(long rateLimitMs) { this.rateLimitMs = rateLimitMs; }
        
        public int getMaxConcurrentDownloads() { return maxConcurrentDownloads; }
        public void setMaxConcurrentDownloads(int maxConcurrentDownloads) { this.maxConcurrentDownloads = maxConcurrentDownloads; }
    }
    
    /**
     * Statistics for bulk crawl operations
     */
    public static class BulkCrawlStatistics {
        private final long totalOperations;
        private final long completedOperations;
        private final long failedOperations;
        private final long activeOperations;
        private final long totalDocumentsSucceeded;
        private final long totalDocumentsFailed;
        
        public BulkCrawlStatistics(Object[] stats) {
            this.totalOperations = stats[0] != null ? ((Number) stats[0]).longValue() : 0;
            this.completedOperations = stats[1] != null ? ((Number) stats[1]).longValue() : 0;
            this.failedOperations = stats[2] != null ? ((Number) stats[2]).longValue() : 0;
            this.activeOperations = stats[3] != null ? ((Number) stats[3]).longValue() : 0;
            this.totalDocumentsSucceeded = stats[4] != null ? ((Number) stats[4]).longValue() : 0;
            this.totalDocumentsFailed = stats[5] != null ? ((Number) stats[5]).longValue() : 0;
        }
        
        // Getters
        public long getTotalOperations() { return totalOperations; }
        public long getCompletedOperations() { return completedOperations; }
        public long getFailedOperations() { return failedOperations; }
        public long getActiveOperations() { return activeOperations; }
        public long getTotalDocumentsSucceeded() { return totalDocumentsSucceeded; }
        public long getTotalDocumentsFailed() { return totalDocumentsFailed; }
        
        public double getSuccessRate() {
            long total = totalDocumentsSucceeded + totalDocumentsFailed;
            return total == 0 ? 0.0 : (double) totalDocumentsSucceeded / total;
        }
        
        public double getOperationSuccessRate() {
            return totalOperations == 0 ? 0.0 : (double) completedOperations / totalOperations;
        }
    }
}