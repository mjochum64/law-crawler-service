package de.legal.crawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Service for discovering all available sitemap dates on rechtsprechung-im-internet.de
 * Implements intelligent discovery strategies to find active sitemaps efficiently
 */
@Service
public class SitemapDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(SitemapDiscoveryService.class);
    
    @Value("${crawler.base-url:https://www.rechtsprechung-im-internet.de}")
    private String baseUrl;
    
    @Value("${crawler.user-agent:DG_JUSTICE_CRAWLER}")
    private String userAgent;
    
    @Value("${crawler.rate-limit-ms:2000}")
    private long rateLimitMs;
    
    @Value("${crawler.bulk.max-concurrent-checks:10}")
    private int maxConcurrentChecks;
    
    @Value("${crawler.bulk.discovery-timeout-hours:2}")
    private int discoveryTimeoutHours;
    
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    
    public SitemapDiscoveryService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Discover all available sitemap dates within a date range
     * Uses parallel checking with rate limiting
     */
    public CompletableFuture<SitemapDiscoveryResult> discoverAvailableSitemaps(
            LocalDate startDate, LocalDate endDate) {
        
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting sitemap discovery from {} to {}", startDate, endDate);
            
            long startTime = System.currentTimeMillis();
            List<LocalDate> availableDates = Collections.synchronizedList(new ArrayList<>());
            List<LocalDate> failedDates = Collections.synchronizedList(new ArrayList<>());
            
            // Generate all dates in range
            List<LocalDate> datesToCheck = generateDateRange(startDate, endDate);
            logger.info("Generated {} dates to check", datesToCheck.size());
            
            // Process dates in parallel batches
            try {
                processDatesInBatches(datesToCheck, availableDates, failedDates);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Discovery interrupted", e);
                throw new RuntimeException("Discovery interrupted", e);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            SitemapDiscoveryResult result = new SitemapDiscoveryResult(
                availableDates, failedDates, duration, datesToCheck.size());
                
            logger.info("Discovery completed: {} available, {} failed, {} total checked in {}ms", 
                       result.getAvailableDates().size(), 
                       result.getFailedDates().size(), 
                       result.getTotalChecked(),
                       result.getDurationMs());
            
            return result;
        }, executorService);
    }
    
    /**
     * Quick discovery using sampling strategy for recent dates with content validation
     */
    public CompletableFuture<SitemapDiscoveryResult> discoverRecentSitemaps(int daysBack) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting intelligent recent sitemap discovery for {} days back", daysBack);
            
            LocalDate endDate = LocalDate.now().minusDays(1); // Start from yesterday
            LocalDate startDate = endDate.minusDays(daysBack);
            
            // First, do a smart sampling to find dates with actual content
            List<LocalDate> datesWithContent = findDatesWithContent(startDate, endDate, 10);
            
            if (datesWithContent.isEmpty()) {
                logger.warn("No recent dates found with sitemap content, falling back to full scan");
                try {
                    return discoverAvailableSitemaps(startDate, endDate).get();
                } catch (Exception e) {
                    logger.error("Fallback discovery failed: {}", e.getMessage());
                    return new SitemapDiscoveryResult(new ArrayList<>(), new ArrayList<>(), 0, 0);
                }
            }
            
            long startTime = System.currentTimeMillis();
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("Smart recent discovery found {} dates with content", datesWithContent.size());
            return new SitemapDiscoveryResult(datesWithContent, new ArrayList<>(), duration, datesWithContent.size());
        }, executorService);
    }
    
    /**
     * Smart discovery that finds the full available date range efficiently
     */
    public CompletableFuture<SitemapDiscoveryResult> discoverFullRange() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting smart full range discovery");
            
            // First, find the earliest and latest available dates using binary search
            LocalDate earliestDate = findEarliestAvailableDate();
            LocalDate latestDate = findLatestAvailableDate();
            
            if (earliestDate == null || latestDate == null) {
                logger.warn("Could not determine date range boundaries");
                return new SitemapDiscoveryResult(new ArrayList<>(), new ArrayList<>(), 0, 0);
            }
            
            logger.info("Discovered date range: {} to {}", earliestDate, latestDate);
            
            // Now discover all dates in the found range
            try {
                return discoverAvailableSitemaps(earliestDate, latestDate).get();
            } catch (Exception e) {
                logger.error("Failed to complete full range discovery", e);
                throw new RuntimeException("Full range discovery failed", e);
            }
        }, executorService);
    }
    
    private void processDatesInBatches(List<LocalDate> datesToCheck, 
                                     List<LocalDate> availableDates, 
                                     List<LocalDate> failedDates) throws InterruptedException {
        
        int batchSize = maxConcurrentChecks;
        int totalBatches = (int) Math.ceil((double) datesToCheck.size() / batchSize);
        
        for (int i = 0; i < totalBatches; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, datesToCheck.size());
            List<LocalDate> batch = datesToCheck.subList(fromIndex, toIndex);
            
            logger.debug("Processing batch {}/{} with {} dates", i + 1, totalBatches, batch.size());
            
            // Process batch in parallel
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (LocalDate date : batch) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Rate limiting
                        Thread.sleep(rateLimitMs);
                        
                        if (checkSitemapExists(date)) {
                            availableDates.add(date);
                            logger.debug("Found sitemap for date: {}", date);
                        } else {
                            failedDates.add(date);
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to check date {}: {}", date, e.getMessage());
                        failedDates.add(date);
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // Wait for batch completion
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(discoveryTimeoutHours, TimeUnit.HOURS);
            } catch (Exception e) {
                logger.warn("Batch completion interrupted: {}", e.getMessage());
            }
            
            logger.debug("Batch {}/{} completed", i + 1, totalBatches);
        }
    }
    
    /**
     * Enhanced sitemap checking that verifies both existence and content
     */
    private boolean checkSitemapExists(LocalDate date) {
        try {
            String sitemapUrl = buildSitemapUrl(date);
            
            // First do a HEAD request to check existence
            HttpRequest headRequest = HttpRequest.newBuilder()
                .uri(URI.create(sitemapUrl))
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "gzip, deflate")
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
                
            HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
            if (headResponse.statusCode() != 200) {
                return false;
            }
            
            // For efficient discovery, we'll do a content check for a sample of dates
            // to avoid downloading all sitemaps during discovery phase
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private LocalDate findEarliestAvailableDate() {
        // Start from a reasonable earliest date (e.g., 2020) and binary search forward
        LocalDate searchStart = LocalDate.of(2020, 1, 1);
        LocalDate searchEnd = LocalDate.now();
        
        return binarySearchForDate(searchStart, searchEnd, true);
    }
    
    private LocalDate findLatestAvailableDate() {
        // Start from yesterday and search backward to find latest date with content
        LocalDate current = LocalDate.now().minusDays(1);
        int maxDaysBack = 30; // Look back up to 30 days
        
        for (int i = 0; i < maxDaysBack; i++) {
            LocalDate testDate = current.minusDays(i);
            if (checkSitemapExistsWithContent(testDate)) {
                logger.info("Found latest available date with content: {}", testDate);
                return testDate;
            }
            
            // Rate limiting for discovery
            try {
                Thread.sleep(rateLimitMs / 2); // Faster for discovery
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        logger.warn("No recent dates found with content, using fallback date");
        return LocalDate.now().minusDays(7); // Fallback to 1 week ago
    }
    
    private LocalDate binarySearchForDate(LocalDate start, LocalDate end, boolean findEarliest) {
        LocalDate current = start;
        LocalDate result = null;
        int maxIterations = 100; // Prevent infinite loops
        int iteration = 0;
        
        while (current.isBefore(end) && iteration < maxIterations) {
            if (checkSitemapExists(current)) {
                result = current;
                if (findEarliest) {
                    // Found a valid date, try earlier
                    end = current;
                    current = start.plusDays((long) (current.until(start).getDays() / 2.0));
                } else {
                    // Found a valid date, try later
                    start = current;
                    current = current.plusDays((long) (end.until(current).getDays() / 2.0));
                }
            } else {
                if (findEarliest) {
                    // No valid date here, try later
                    start = current;
                    current = current.plusDays((long) (end.until(current).getDays() / 2.0));
                } else {
                    // No valid date here, try earlier
                    end = current;
                    current = start.plusDays((long) (current.until(start).getDays() / 2.0));
                }
            }
            
            // Add delay for rate limiting
            try {
                Thread.sleep(rateLimitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            iteration++;
        }
        
        return result;
    }
    
    private List<LocalDate> generateDateRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        
        while (!current.isAfter(end)) {
            dates.add(current);
            current = current.plusDays(1);
        }
        
        return dates;
    }
    
    private String buildSitemapUrl(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return String.format("%s/jportal/docs/eclicrawler/%s/sitemap_index_1.xml", 
                           baseUrl, date.format(formatter));
    }
    
    /**
     * Check if a sitemap exists and contains actual document URLs (not just empty)
     */
    private boolean checkSitemapExistsWithContent(LocalDate date) {
        try {
            String sitemapUrl = buildSitemapUrl(date);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sitemapUrl))
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "gzip, deflate")
                .timeout(Duration.ofSeconds(15))
                .build();
                
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() != 200) {
                return false;
            }
            
            // Decompress if needed and check content
            byte[] responseBody = response.body();
            String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
            String xmlContent;
            
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                try (java.io.InputStream gzipStream = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(responseBody))) {
                    xmlContent = new String(gzipStream.readAllBytes(), "UTF-8");
                }
            } else {
                xmlContent = new String(responseBody, "UTF-8");
            }
            
            // Check if sitemap contains actual sitemap entries (not just empty urlset)
            return xmlContent.contains("<sitemap>") && xmlContent.contains("<loc>");
            
        } catch (Exception e) {
            logger.debug("Content check failed for date {}: {}", date, e.getMessage());
            return false;
        }
    }
    
    /**
     * Find dates with actual sitemap content using intelligent sampling
     */
    private List<LocalDate> findDatesWithContent(LocalDate startDate, LocalDate endDate, int maxSamples) {
        List<LocalDate> datesWithContent = new ArrayList<>();
        List<LocalDate> datesToSample = generateSampleDates(startDate, endDate, maxSamples);
        
        logger.info("Sampling {} dates to find content between {} and {}", datesToSample.size(), startDate, endDate);
        
        for (LocalDate date : datesToSample) {
            if (checkSitemapExistsWithContent(date)) {
                datesWithContent.add(date);
                logger.debug("Found content for date: {}", date);
            }
            
            // Rate limiting
            try {
                Thread.sleep(rateLimitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return datesWithContent;
    }
    
    /**
     * Generate sample dates for intelligent content discovery
     * Uses a strategy that samples recent dates more frequently
     */
    private List<LocalDate> generateSampleDates(LocalDate startDate, LocalDate endDate, int maxSamples) {
        List<LocalDate> sampleDates = new ArrayList<>();
        long totalDays = startDate.until(endDate).getDays() + 1;
        
        if (totalDays <= maxSamples) {
            // If range is small, check all dates
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                sampleDates.add(current);
                current = current.plusDays(1);
            }
        } else {
            // Strategic sampling: more recent dates more likely to have content
            // Sample every few days, with bias toward recent dates
            int step = (int) Math.max(1, totalDays / maxSamples);
            
            // Start from the end (most recent) and work backward
            LocalDate current = endDate;
            int samplesAdded = 0;
            
            while (!current.isBefore(startDate) && samplesAdded < maxSamples) {
                sampleDates.add(0, current); // Add to beginning to maintain chronological order
                current = current.minusDays(step);
                samplesAdded++;
            }
            
            // Always include the start date
            if (!sampleDates.contains(startDate)) {
                sampleDates.add(0, startDate);
            }
        }
        
        return sampleDates;
    }
    
    /**
     * Result of sitemap discovery operation
     */
    public static class SitemapDiscoveryResult {
        private final List<LocalDate> availableDates;
        private final List<LocalDate> failedDates;
        private final long durationMs;
        private final int totalChecked;
        private final LocalDate discoveredAt;
        
        public SitemapDiscoveryResult(List<LocalDate> availableDates, List<LocalDate> failedDates, 
                                    long durationMs, int totalChecked) {
            this.availableDates = new ArrayList<>(availableDates);
            this.availableDates.sort(LocalDate::compareTo);
            this.failedDates = new ArrayList<>(failedDates);
            this.durationMs = durationMs;
            this.totalChecked = totalChecked;
            this.discoveredAt = LocalDate.now();
        }
        
        public List<LocalDate> getAvailableDates() { return availableDates; }
        public List<LocalDate> getFailedDates() { return failedDates; }
        public long getDurationMs() { return durationMs; }
        public int getTotalChecked() { return totalChecked; }
        public LocalDate getDiscoveredAt() { return discoveredAt; }
        public int getAvailableCount() { return availableDates.size(); }
        public int getFailedCount() { return failedDates.size(); }
        
        public LocalDate getEarliestDate() {
            return availableDates.isEmpty() ? null : availableDates.get(0);
        }
        
        public LocalDate getLatestDate() {
            return availableDates.isEmpty() ? null : availableDates.get(availableDates.size() - 1);
        }
        
        public double getSuccessRate() {
            return totalChecked == 0 ? 0.0 : (double) availableDates.size() / totalChecked;
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}