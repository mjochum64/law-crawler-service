package de.legal.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Legal Document Crawler Service
 * Crawls legal documents from rechtsprechung-im-internet.de
 */
@SpringBootApplication
@EnableScheduling
public class LawCrawlerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawCrawlerServiceApplication.class, args);
    }
}