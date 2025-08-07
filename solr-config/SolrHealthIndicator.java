package de.legal.crawler.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Solr connectivity
 */
@Component
public class SolrHealthIndicator implements HealthIndicator {

    private final SolrClient solrClient;

    public SolrHealthIndicator(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    @Override
    public Health health() {
        try {
            // Simple ping query to check connectivity
            SolrQuery query = new SolrQuery("*:*");
            query.setRows(0); // Only check connectivity, don't fetch data
            
            long startTime = System.currentTimeMillis();
            QueryResponse response = solrClient.query(query);
            long responseTime = System.currentTimeMillis() - startTime;
            
            long documentCount = response.getResults().getNumFound();
            
            return Health.up()
                .withDetail("responseTime", responseTime + "ms")
                .withDetail("documentCount", documentCount)
                .withDetail("status", "Connected")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Disconnected")
                .build();
        }
    }
}