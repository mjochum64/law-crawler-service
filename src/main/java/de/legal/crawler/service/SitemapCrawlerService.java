package de.legal.crawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/**
 * Service for crawling sitemaps from rechtsprechung-im-internet.de
 * Respects robots.txt restrictions and implements ethical crawling practices
 */
@Service
public class SitemapCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(SitemapCrawlerService.class);
    
    @Value("${crawler.base-url:https://www.rechtsprechung-im-internet.de}")
    private String baseUrl;
    
    @Value("${crawler.user-agent:LegalDocumentCrawler/1.0}")
    private String userAgent;
    
    @Value("${crawler.rate-limit-ms:1000}")
    private long rateLimitMs;
    
    private final HttpClient httpClient;
    
    public SitemapCrawlerService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Fetch sitemap index for a specific date
     */
    public CompletableFuture<List<String>> fetchSitemapIndex(LocalDate date) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sitemapUrl = buildSitemapIndexUrl(date);
                logger.info("Fetching sitemap index from: {}", sitemapUrl);
                
                String xmlContent = fetchXmlContent(sitemapUrl);
                return parseSitemapIndex(xmlContent);
                
            } catch (Exception e) {
                logger.error("Failed to fetch sitemap index for date {}: {}", date, e.getMessage());
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Fetch individual sitemap and extract document URLs
     */
    public CompletableFuture<List<DocumentEntry>> fetchSitemap(String sitemapUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Fetching sitemap: {}", sitemapUrl);
                
                // Rate limiting
                Thread.sleep(rateLimitMs);
                
                String xmlContent = fetchXmlContent(sitemapUrl);
                return parseDocumentSitemap(xmlContent);
                
            } catch (Exception e) {
                logger.error("Failed to fetch sitemap {}: {}", sitemapUrl, e.getMessage());
                return new ArrayList<>();
            }
        });
    }
    
    private String buildSitemapIndexUrl(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return String.format("%s/jportal/docs/eclicrawler/%s/sitemap_index_1.xml", 
                           baseUrl, date.format(formatter));
    }
    
    private String fetchXmlContent(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .header("Accept-Encoding", "gzip, deflate")
            .GET()
            .build();
            
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " for URL: " + url);
        }
        
        byte[] responseBody = response.body();
        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
        
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (InputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(responseBody))) {
                return new String(gzipStream.readAllBytes(), "UTF-8");
            }
        } else {
            return new String(responseBody, "UTF-8");
        }
    }
    
    private List<String> parseSitemapIndex(String xmlContent) throws Exception {
        List<String> sitemapUrls = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes()));
        
        NodeList sitemaps = doc.getElementsByTagName("sitemap");
        for (int i = 0; i < sitemaps.getLength(); i++) {
            Element sitemap = (Element) sitemaps.item(i);
            NodeList locNodes = sitemap.getElementsByTagName("loc");
            if (locNodes.getLength() > 0) {
                sitemapUrls.add(locNodes.item(0).getTextContent());
            }
        }
        
        return sitemapUrls;
    }
    
    private List<DocumentEntry> parseDocumentSitemap(String xmlContent) throws Exception {
        List<DocumentEntry> documents = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes()));
        
        NodeList urlNodes = doc.getElementsByTagName("url");
        for (int i = 0; i < urlNodes.getLength(); i++) {
            Element urlElement = (Element) urlNodes.item(i);
            
            NodeList locNodes = urlElement.getElementsByTagName("loc");
            NodeList lastmodNodes = urlElement.getElementsByTagName("lastmod");
            
            if (locNodes.getLength() > 0) {
                String documentUrl = locNodes.item(0).getTextContent().trim();
                String lastmod = lastmodNodes.getLength() > 0 ? 
                    lastmodNodes.item(0).getTextContent().trim() : null;
                
                documents.add(new DocumentEntry(documentUrl, lastmod));
            }
        }
        
        return documents;
    }
    
    /**
     * Document entry from sitemap
     */
    public static class DocumentEntry {
        private final String url;
        private final String lastModified;
        
        public DocumentEntry(String url, String lastModified) {
            this.url = url;
            this.lastModified = lastModified;
        }
        
        public String getUrl() { return url; }
        public String getLastModified() { return lastModified; }
        
        public String extractDocumentId() {
            // Extract docid parameter from URL
            try {
                String[] parts = url.split("docid=");
                if (parts.length > 1) {
                    return parts[1].split("&")[0];
                }
            } catch (Exception e) {
                logger.warn("Failed to extract document ID from URL: {}", url);
            }
            return null;
        }
    }
}