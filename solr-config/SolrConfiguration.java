package de.legal.crawler.config;

import de.legal.crawler.repository.LegalDocumentRepository;
import de.legal.crawler.repository.SolrDocumentRepository;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for Solr integration
 * Replaces H2/JPA setup when enabled
 */
@Configuration
@ConditionalOnProperty(name = "crawler.storage.type", havingValue = "solr")
public class SolrConfiguration {

    @Value("${solr.url:http://localhost:8983/solr}")
    private String solrUrl;

    @Value("${solr.collection:legal-documents}")
    private String solrCollection;

    @Value("${solr.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${solr.socket-timeout:30000}")
    private int socketTimeout;

    /**
     * Primary SolrClient for HTTP/2 communication
     */
    @Bean
    @Primary
    public SolrClient solrClient() {
        return new Http2SolrClient.Builder(solrUrl + "/" + solrCollection)
            .withConnectionTimeout(connectionTimeout)
            .withRequestTimeout(socketTimeout)
            .build();
    }

    /**
     * Solr-based repository implementation
     */
    @Bean
    @Primary
    public LegalDocumentRepository solrDocumentRepository(SolrClient solrClient) {
        return new SolrDocumentRepository(solrUrl, solrCollection);
    }

    /**
     * Health indicator for Solr connection
     */
    @Bean
    public SolrHealthIndicator solrHealthIndicator(SolrClient solrClient) {
        return new SolrHealthIndicator(solrClient);
    }
}