package de.legal.crawler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity for tracking bulk crawling operation progress
 */
@Entity
@Table(name = "bulk_crawl_progress")
public class BulkCrawlProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true, nullable = false)
    private String operationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BulkCrawlStatus status = BulkCrawlStatus.INITIALIZING;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime pausedAt;

    @Column
    private int totalDatesDiscovered = 0;

    @Column
    private int datesProcessed = 0;

    @Column
    private int totalSitemapsFound = 0;

    @Column
    private int sitemapsProcessed = 0;

    @Column
    private int totalDocumentsFound = 0;

    @Column
    private int documentsProcessed = 0;

    @Column
    private int documentsSucceeded = 0;

    @Column
    private int documentsFailed = 0;

    @Column
    private long estimatedTotalDocuments = 0;

    @Column
    private long estimatedCompletionTimeMs = 0;

    @Column
    private double processingRateDocsPerMinute = 0.0;

    @Column
    private String currentPhase; // DISCOVERY, CRAWLING, COMPLETING

    @Column
    private LocalDate currentProcessingDate;

    @Column
    private String currentSitemapUrl;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private int retryCount = 0;

    @Column
    private boolean pauseRequested = false;

    @Column
    private boolean cancelRequested = false;

    // Configuration
    @Column
    private boolean forceUpdate = false;

    @Column
    private long rateLimitMs = 2000;

    @Column
    private int maxConcurrentDownloads = 5;

    // Statistics
    @Column
    private long totalProcessingTimeMs = 0;

    @Column
    private long discoveryTimeMs = 0;

    @Column
    private long downloadTimeMs = 0;

    @ElementCollection
    @CollectionTable(name = "bulk_crawl_processed_dates", 
                    joinColumns = @JoinColumn(name = "bulk_crawl_id"))
    @Column(name = "processed_date")
    private List<LocalDate> processedDates = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "bulk_crawl_failed_dates", 
                    joinColumns = @JoinColumn(name = "bulk_crawl_id"))
    @Column(name = "failed_date")
    private List<LocalDate> failedDates = new ArrayList<>();

    // Constructors
    public BulkCrawlProgress() {
        this.createdAt = LocalDateTime.now();
    }

    public BulkCrawlProgress(String operationId, LocalDate startDate, LocalDate endDate) {
        this();
        this.operationId = operationId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    public BulkCrawlStatus getStatus() { return status; }
    public void setStatus(BulkCrawlStatus status) { this.status = status; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getPausedAt() { return pausedAt; }
    public void setPausedAt(LocalDateTime pausedAt) { this.pausedAt = pausedAt; }

    public int getTotalDatesDiscovered() { return totalDatesDiscovered; }
    public void setTotalDatesDiscovered(int totalDatesDiscovered) { this.totalDatesDiscovered = totalDatesDiscovered; }

    public int getDatesProcessed() { return datesProcessed; }
    public void setDatesProcessed(int datesProcessed) { this.datesProcessed = datesProcessed; }

    public int getTotalSitemapsFound() { return totalSitemapsFound; }
    public void setTotalSitemapsFound(int totalSitemapsFound) { this.totalSitemapsFound = totalSitemapsFound; }

    public int getSitemapsProcessed() { return sitemapsProcessed; }
    public void setSitemapsProcessed(int sitemapsProcessed) { this.sitemapsProcessed = sitemapsProcessed; }

    public int getTotalDocumentsFound() { return totalDocumentsFound; }
    public void setTotalDocumentsFound(int totalDocumentsFound) { this.totalDocumentsFound = totalDocumentsFound; }

    public int getDocumentsProcessed() { return documentsProcessed; }
    public void setDocumentsProcessed(int documentsProcessed) { this.documentsProcessed = documentsProcessed; }

    public int getDocumentsSucceeded() { return documentsSucceeded; }
    public void setDocumentsSucceeded(int documentsSucceeded) { this.documentsSucceeded = documentsSucceeded; }

    public int getDocumentsFailed() { return documentsFailed; }
    public void setDocumentsFailed(int documentsFailed) { this.documentsFailed = documentsFailed; }

    public long getEstimatedTotalDocuments() { return estimatedTotalDocuments; }
    public void setEstimatedTotalDocuments(long estimatedTotalDocuments) { this.estimatedTotalDocuments = estimatedTotalDocuments; }

    public long getEstimatedCompletionTimeMs() { return estimatedCompletionTimeMs; }
    public void setEstimatedCompletionTimeMs(long estimatedCompletionTimeMs) { this.estimatedCompletionTimeMs = estimatedCompletionTimeMs; }

    public double getProcessingRateDocsPerMinute() { return processingRateDocsPerMinute; }
    public void setProcessingRateDocsPerMinute(double processingRateDocsPerMinute) { this.processingRateDocsPerMinute = processingRateDocsPerMinute; }

    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }

    public LocalDate getCurrentProcessingDate() { return currentProcessingDate; }
    public void setCurrentProcessingDate(LocalDate currentProcessingDate) { this.currentProcessingDate = currentProcessingDate; }

    public String getCurrentSitemapUrl() { return currentSitemapUrl; }
    public void setCurrentSitemapUrl(String currentSitemapUrl) { this.currentSitemapUrl = currentSitemapUrl; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public boolean isPauseRequested() { return pauseRequested; }
    public void setPauseRequested(boolean pauseRequested) { this.pauseRequested = pauseRequested; }

    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean cancelRequested) { this.cancelRequested = cancelRequested; }

    public boolean isForceUpdate() { return forceUpdate; }
    public void setForceUpdate(boolean forceUpdate) { this.forceUpdate = forceUpdate; }

    public long getRateLimitMs() { return rateLimitMs; }
    public void setRateLimitMs(long rateLimitMs) { this.rateLimitMs = rateLimitMs; }

    public int getMaxConcurrentDownloads() { return maxConcurrentDownloads; }
    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) { this.maxConcurrentDownloads = maxConcurrentDownloads; }

    public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
    public void setTotalProcessingTimeMs(long totalProcessingTimeMs) { this.totalProcessingTimeMs = totalProcessingTimeMs; }

    public long getDiscoveryTimeMs() { return discoveryTimeMs; }
    public void setDiscoveryTimeMs(long discoveryTimeMs) { this.discoveryTimeMs = discoveryTimeMs; }

    public long getDownloadTimeMs() { return downloadTimeMs; }
    public void setDownloadTimeMs(long downloadTimeMs) { this.downloadTimeMs = downloadTimeMs; }

    public List<LocalDate> getProcessedDates() { return processedDates; }
    public void setProcessedDates(List<LocalDate> processedDates) { this.processedDates = processedDates; }

    public List<LocalDate> getFailedDates() { return failedDates; }
    public void setFailedDates(List<LocalDate> failedDates) { this.failedDates = failedDates; }

    // Convenience methods
    public double getProgressPercentage() {
        if (estimatedTotalDocuments == 0) {
            return totalDatesDiscovered == 0 ? 0.0 : ((double) datesProcessed / totalDatesDiscovered) * 100.0;
        }
        return ((double) documentsProcessed / estimatedTotalDocuments) * 100.0;
    }

    public boolean isRunning() {
        return status == BulkCrawlStatus.DISCOVERING || 
               status == BulkCrawlStatus.CRAWLING ||
               status == BulkCrawlStatus.RESUMING;
    }

    public boolean isCompleted() {
        return status == BulkCrawlStatus.COMPLETED || 
               status == BulkCrawlStatus.CANCELLED ||
               status == BulkCrawlStatus.FAILED;
    }

    public boolean isPaused() {
        return status == BulkCrawlStatus.PAUSED;
    }

    public void incrementProcessedDates() {
        this.datesProcessed++;
    }

    public void incrementSitemapsProcessed() {
        this.sitemapsProcessed++;
    }

    public void incrementDocumentsProcessed() {
        this.documentsProcessed++;
    }

    public void incrementDocumentsSucceeded() {
        this.documentsSucceeded++;
    }

    public void incrementDocumentsFailed() {
        this.documentsFailed++;
    }

    public void addProcessedDate(LocalDate date) {
        if (!this.processedDates.contains(date)) {
            this.processedDates.add(date);
        }
    }

    public void addFailedDate(LocalDate date) {
        if (!this.failedDates.contains(date)) {
            this.failedDates.add(date);
        }
    }

    public long getDurationMs() {
        if (startedAt == null) return 0;
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, endTime).toMillis();
    }

    public void updateProcessingRate() {
        long durationMs = getDurationMs();
        if (durationMs > 0 && documentsProcessed > 0) {
            double durationMinutes = durationMs / (1000.0 * 60.0);
            this.processingRateDocsPerMinute = documentsProcessed / durationMinutes;
        }
    }

    public void updateEstimatedCompletion() {
        if (processingRateDocsPerMinute > 0 && estimatedTotalDocuments > documentsProcessed) {
            long remainingDocs = estimatedTotalDocuments - documentsProcessed;
            double remainingMinutes = remainingDocs / processingRateDocsPerMinute;
            this.estimatedCompletionTimeMs = System.currentTimeMillis() + (long)(remainingMinutes * 60 * 1000);
        }
    }

    public enum BulkCrawlStatus {
        INITIALIZING,    // Setting up the bulk crawl operation
        DISCOVERING,     // Discovering available sitemap dates
        CRAWLING,        // Actively crawling documents
        PAUSED,         // Temporarily paused
        RESUMING,       // Resuming from pause
        COMPLETED,      // Successfully completed
        FAILED,         // Failed with errors
        CANCELLED       // Cancelled by user
    }
}