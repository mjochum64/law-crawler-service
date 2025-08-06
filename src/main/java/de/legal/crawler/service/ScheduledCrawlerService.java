package de.legal.crawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for scheduled crawling operations
 * Automatically crawls legal documents at regular intervals
 */
@Service
public class ScheduledCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCrawlerService.class);
    
    @Autowired
    private CrawlerOrchestrationService orchestrationService;
    
    @Value("${crawler.scheduled.enabled:true}")
    private boolean scheduledCrawlingEnabled;
    
    @Value("${crawler.scheduled.days-back:7}")
    private int daysBack;
    
    /**
     * Daily crawling job - runs every day at 6 AM
     * Crawls documents from the last few days
     */
    @Scheduled(cron = "${crawler.scheduled.daily-cron:0 0 6 * * ?}")
    public void dailyCrawl() {
        if (!scheduledCrawlingEnabled) {
            logger.debug("Scheduled crawling is disabled");
            return;
        }
        
        logger.info("Starting scheduled daily crawl");
        
        try {
            // Crawl documents from the last 'daysBack' days
            LocalDate endDate = LocalDate.now().minusDays(1); // Yesterday
            LocalDate startDate = endDate.minusDays(daysBack - 1);
            
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                try {
                    logger.info("Scheduled crawl for date: {}", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    orchestrationService.startCrawling(date, false);
                    
                    // Brief pause between dates to avoid overwhelming the server
                    Thread.sleep(5000);
                    
                } catch (Exception e) {
                    logger.error("Scheduled crawl failed for date {}: {}", date, e.getMessage());
                }
            }
            
            logger.info("Scheduled daily crawl completed");
            
        } catch (Exception e) {
            logger.error("Daily crawl failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Weekly deep crawl - runs every Sunday at 2 AM
     * Performs a comprehensive crawl with force update
     */
    @Scheduled(cron = "${crawler.scheduled.weekly-cron:0 0 2 * * SUN}")
    public void weeklyCrawl() {
        if (!scheduledCrawlingEnabled) {
            logger.debug("Scheduled crawling is disabled");
            return;
        }
        
        logger.info("Starting scheduled weekly crawl");
        
        try {
            // Crawl documents from the last 30 days with force update
            LocalDate endDate = LocalDate.now().minusDays(1);
            LocalDate startDate = endDate.minusDays(29);
            
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                try {
                    logger.info("Weekly crawl for date: {}", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    orchestrationService.startCrawling(date, true); // Force update
                    
                    // Longer pause for weekly crawl
                    Thread.sleep(10000);
                    
                } catch (Exception e) {
                    logger.error("Weekly crawl failed for date {}: {}", date, e.getMessage());
                }
            }
            
            logger.info("Scheduled weekly crawl completed");
            
        } catch (Exception e) {
            logger.error("Weekly crawl failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Retry failed documents - runs every 6 hours
     */
    @Scheduled(cron = "${crawler.scheduled.retry-cron:0 0 */6 * * ?}")
    public void retryFailedDocuments() {
        if (!scheduledCrawlingEnabled) {
            logger.debug("Scheduled crawling is disabled");
            return;
        }
        
        logger.info("Starting scheduled retry of failed documents");
        
        try {
            orchestrationService.retryFailedDocuments();
            logger.info("Scheduled retry completed");
            
        } catch (Exception e) {
            logger.error("Scheduled retry failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * System health check - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void systemHealthCheck() {
        if (!scheduledCrawlingEnabled) {
            return;
        }
        
        try {
            // Log basic system status
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            logger.info("System health - Memory usage: {} MB / {} MB", 
                       usedMemory / (1024 * 1024), 
                       totalMemory / (1024 * 1024));
            
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());
        }
    }
}