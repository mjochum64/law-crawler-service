package de.legal.crawler.repository;

import de.legal.crawler.model.LegalDocument;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Solr-based Repository implementation replacing H2/JPA
 * Provides all CRUD operations directly against Apache Solr
 */
@Repository("legalDocumentRepository")
@org.springframework.context.annotation.Profile("solr")
public class SolrDocumentRepository implements LegalDocumentRepository {

    private static final Logger logger = LoggerFactory.getLogger(SolrDocumentRepository.class);
    
    private final SolrClient solrClient;
    private final String collection;
    
    public SolrDocumentRepository(
            @Value("${solr.url:http://localhost:8983/solr}") String solrUrl,
            @Value("${solr.collection:legal-documents}") String collection) {
        this.collection = collection;
        this.solrClient = new Http2SolrClient.Builder(solrUrl + "/" + collection).build();
        logger.info("SolrDocumentRepository initialized for collection: {} at {}", collection, solrUrl);
    }

    // ==================== CRUD OPERATIONS ====================

    @Override
    public <S extends LegalDocument> S save(S document) {
        try {
            SolrInputDocument solrDoc = convertToSolrDocument(document);
            UpdateResponse response = solrClient.add(solrDoc);
            solrClient.commit();
            
            logger.debug("Saved document {} to Solr, response time: {}ms", 
                        document.getDocumentId(), response.getElapsedTime());
            return document;
            
        } catch (Exception e) {
            logger.error("Failed to save document {} to Solr", document.getDocumentId(), e);
            throw new RuntimeException("Solr save failed", e);
        }
    }

    @Override
    public <S extends LegalDocument> Iterable<S> saveAll(Iterable<S> documents) {
        try {
            List<SolrInputDocument> solrDocs = new ArrayList<>();
            for (S document : documents) {
                solrDocs.add(convertToSolrDocument(document));
            }
            
            UpdateResponse response = solrClient.add(solrDocs);
            solrClient.commit();
            
            logger.info("Batch saved {} documents to Solr, response time: {}ms", 
                       solrDocs.size(), response.getElapsedTime());
            return documents;
            
        } catch (Exception e) {
            logger.error("Failed to batch save documents to Solr", e);
            throw new RuntimeException("Solr batch save failed", e);
        }
    }

    @Override
    public Optional<LegalDocument> findById(Long id) {
        return findByDocumentId("legal_" + id);
    }

    @Override
    public Optional<LegalDocument> findByDocumentId(String documentId) {
        try {
            SolrQuery query = new SolrQuery("document_id:" + escapeQueryValue(documentId));
            QueryResponse response = solrClient.query(query);
            
            if (response.getResults().getNumFound() > 0) {
                SolrDocument solrDoc = response.getResults().get(0);
                return Optional.of(convertFromSolrDocument(solrDoc));
            }
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Failed to find document by ID: {}", documentId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public boolean existsBySourceUrl(String sourceUrl) {
        try {
            SolrQuery query = new SolrQuery("source_url:" + escapeQueryValue(sourceUrl));
            query.setRows(0); // Only count
            QueryResponse response = solrClient.query(query);
            return response.getResults().getNumFound() > 0;
        } catch (Exception e) {
            logger.error("Failed to check existence by URL: {}", sourceUrl, e);
            return false;
        }
    }

    @Override
    public Iterable<LegalDocument> findAll() {
        return findAllWithQuery("*:*");
    }

    @Override
    public Iterable<LegalDocument> findAllById(Iterable<Long> ids) {
        List<String> documentIds = new ArrayList<>();
        for (Long id : ids) {
            documentIds.add("legal_" + id);
        }
        
        String query = "document_id:(" + String.join(" OR ", documentIds) + ")";
        return findAllWithQuery(query);
    }

    @Override
    public long count() {
        try {
            SolrQuery query = new SolrQuery("*:*");
            query.setRows(0);
            QueryResponse response = solrClient.query(query);
            return response.getResults().getNumFound();
        } catch (Exception e) {
            logger.error("Failed to count documents", e);
            return 0L;
        }
    }

    @Override
    public void deleteById(Long id) {
        deleteByDocumentId("legal_" + id);
    }

    private void deleteByDocumentId(String documentId) {
        try {
            UpdateResponse response = solrClient.deleteByQuery("document_id:" + escapeQueryValue(documentId));
            solrClient.commit();
            logger.debug("Deleted document {} from Solr", documentId);
        } catch (Exception e) {
            logger.error("Failed to delete document: {}", documentId, e);
            throw new RuntimeException("Solr delete failed", e);
        }
    }

    @Override
    public void delete(LegalDocument document) {
        deleteByDocumentId(document.getDocumentId());
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        for (Long id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends LegalDocument> documents) {
        for (LegalDocument doc : documents) {
            delete(doc);
        }
    }

    @Override
    public void deleteAll() {
        try {
            solrClient.deleteByQuery("*:*");
            solrClient.commit();
            logger.warn("Deleted ALL documents from Solr collection: {}", collection);
        } catch (Exception e) {
            logger.error("Failed to delete all documents", e);
            throw new RuntimeException("Solr delete all failed", e);
        }
    }

    // ==================== CUSTOM QUERY METHODS ====================

    @Override
    public List<LegalDocument> findByCourtOrderByDecisionDateDesc(String court) {
        String query = "court:" + escapeQueryValue(court);
        return findAllWithQueryAndSort(query, "decision_date desc");
    }

    @Override
    public List<LegalDocument> findByStatus(LegalDocument.DocumentStatus status) {
        String query = "status:" + escapeQueryValue(status.name());
        return findAllWithQuery(query);
    }

    @Override
    public List<LegalDocument> findByCourtAndStatus(String court, LegalDocument.DocumentStatus status) {
        String query = "court:" + escapeQueryValue(court) + " AND status:" + escapeQueryValue(status.name());
        return findAllWithQuery(query);
    }

    @Override
    public List<LegalDocument> findByDecisionDateBetween(LocalDateTime startDate, LocalDateTime endDate) {
        String startStr = startDate.atZone(ZoneOffset.UTC).toString().replace("[UTC]", "Z");
        String endStr = endDate.atZone(ZoneOffset.UTC).toString().replace("[UTC]", "Z");
        String query = "decision_date:[" + startStr + " TO " + endStr + "]";
        return findAllWithQueryAndSort(query, "decision_date desc");
    }

    @Override
    public List<LegalDocument> findByCrawledAtAfterOrderByCrawledAtDesc(LocalDateTime crawledAfter) {
        String dateStr = crawledAfter.atZone(ZoneOffset.UTC).toString().replace("[UTC]", "Z");
        String query = "crawled_at:[" + dateStr + " TO NOW]";
        return findAllWithQueryAndSort(query, "crawled_at desc");
    }

    @Override
    public Optional<LegalDocument> findByEcliIdentifier(String ecliIdentifier) {
        try {
            SolrQuery query = new SolrQuery("ecli_identifier:" + escapeQueryValue(ecliIdentifier));
            QueryResponse response = solrClient.query(query);
            
            if (response.getResults().getNumFound() > 0) {
                return Optional.of(convertFromSolrDocument(response.getResults().get(0)));
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to find document by ECLI: {}", ecliIdentifier, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Object[]> countDocumentsByCourt() {
        try {
            SolrQuery query = new SolrQuery("*:*");
            query.setFacet(true);
            query.addFacetField("court");
            query.setRows(0);
            
            QueryResponse response = solrClient.query(query);
            return response.getFacetField("court").getValues().stream()
                .map(count -> new Object[]{count.getName(), count.getCount()})
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Failed to count documents by court", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Object[]> countDocumentsByStatus() {
        try {
            SolrQuery query = new SolrQuery("*:*");
            query.setFacet(true);
            query.addFacetField("status");
            query.setRows(0);
            
            QueryResponse response = solrClient.query(query);
            return response.getFacetField("status").getValues().stream()
                .map(count -> new Object[]{count.getName(), count.getCount()})
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Failed to count documents by status", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<LegalDocument> findFailedDocumentsForRetry(LocalDateTime retryAfter) {
        String dateStr = retryAfter.atZone(ZoneOffset.UTC).toString().replace("[UTC]", "Z");
        String query = "status:FAILED AND crawled_at:[* TO " + dateStr + "]";
        return findAllWithQuery(query);
    }

    @Override
    public Page<LegalDocument> findByCourtOrderByDecisionDateDesc(String court, Pageable pageable) {
        try {
            SolrQuery query = new SolrQuery("court:" + escapeQueryValue(court));
            query.addSort("decision_date", SolrQuery.ORDER.desc);
            query.setStart((int) pageable.getOffset());
            query.setRows(pageable.getPageSize());
            
            QueryResponse response = solrClient.query(query);
            List<LegalDocument> documents = convertSolrDocumentList(response.getResults());
            
            return new PageImpl<>(documents, pageable, response.getResults().getNumFound());
        } catch (Exception e) {
            logger.error("Failed to find documents by court with pagination", e);
            return Page.empty(pageable);
        }
    }

    @Override
    public List<LegalDocument> findByTitleContainingIgnoreCase(String searchTerm) {
        // Solr's advantage: Full-text search instead of simple LIKE
        String query = "title:*" + escapeQueryValue(searchTerm.toLowerCase()) + "*";
        return findAllWithQuery(query);
    }

    @Override
    public List<LegalDocument> findRecentDocuments(LocalDateTime since) {
        String dateStr = since.atZone(ZoneOffset.UTC).toString().replace("[UTC]", "Z");
        String query = "decision_date:[" + dateStr + " TO NOW]";
        return findAllWithQueryAndSort(query, "decision_date desc");
    }

    // ==================== HELPER METHODS ====================

    private List<LegalDocument> findAllWithQuery(String queryString) {
        return findAllWithQueryAndSort(queryString, null);
    }

    @Override
    public List<LegalDocument> findAllWithQueryAndSort(String queryString, String sort) {
        try {
            SolrQuery query = new SolrQuery(queryString);
            if (sort != null) {
                // Parse sort string like "field desc" or "field asc"
                String[] sortParts = sort.split("\\s+");
                if (sortParts.length >= 2) {
                    String field = sortParts[0];
                    String direction = sortParts[1].toLowerCase();
                    SolrQuery.ORDER order = "desc".equals(direction) ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc;
                    query.addSort(field, order);
                } else if (sortParts.length == 1) {
                    query.addSort(sortParts[0], SolrQuery.ORDER.asc);
                }
            }
            query.setRows(1000); // Reasonable default limit
            
            QueryResponse response = solrClient.query(query);
            return convertSolrDocumentList(response.getResults());
            
        } catch (Exception e) {
            logger.error("Failed to execute query: {}", queryString, e);
            return Collections.emptyList();
        }
    }

    private SolrInputDocument convertToSolrDocument(LegalDocument document) {
        SolrInputDocument solrDoc = new SolrInputDocument();
        
        solrDoc.addField("id", "legal_" + document.getDocumentId());
        solrDoc.addField("document_id", document.getDocumentId());
        solrDoc.addField("court", document.getCourt());
        solrDoc.addField("ecli_identifier", document.getEcliIdentifier());
        solrDoc.addField("source_url", document.getSourceUrl());
        solrDoc.addField("title", document.getTitle());
        solrDoc.addField("summary", document.getSummary());
        solrDoc.addField("file_path", document.getFilePath());
        solrDoc.addField("status", document.getStatus().name());
        solrDoc.addField("document_type", document.getDocumentType());
        solrDoc.addField("full_text", document.getFullText());
        solrDoc.addField("case_number", document.getCaseNumber());
        solrDoc.addField("norms", document.getNorms());
        solrDoc.addField("subject", document.getSubject());
        
        if (document.getDecisionDate() != null) {
            solrDoc.addField("decision_date", 
                document.getDecisionDate().atZone(ZoneOffset.UTC).toInstant());
            solrDoc.addField("year", document.getDecisionDate().getYear());
            solrDoc.addField("month", document.getDecisionDate().getMonthValue());
        }
        
        if (document.getCrawledAt() != null) {
            solrDoc.addField("crawled_at", 
                document.getCrawledAt().atZone(ZoneOffset.UTC).toInstant());
        }
        
        solrDoc.addField("indexed_at", new Date());
        
        return solrDoc;
    }

    private LegalDocument convertFromSolrDocument(SolrDocument solrDoc) {
        LegalDocument document = new LegalDocument();
        
        document.setDocumentId(getStringFieldValue(solrDoc, "document_id"));
        document.setCourt(getStringFieldValue(solrDoc, "court"));
        document.setEcliIdentifier(getStringFieldValue(solrDoc, "ecli_identifier"));
        document.setSourceUrl(getStringFieldValue(solrDoc, "source_url"));
        document.setTitle(getStringFieldValue(solrDoc, "title"));
        document.setSummary(getStringFieldValue(solrDoc, "summary"));
        document.setFilePath(getStringFieldValue(solrDoc, "file_path"));
        document.setDocumentType(getStringFieldValue(solrDoc, "document_type"));
        document.setFullText(getStringFieldValue(solrDoc, "full_text"));
        document.setCaseNumber(getStringFieldValue(solrDoc, "case_number"));
        document.setNorms(getStringFieldValue(solrDoc, "norms"));
        document.setSubject(getStringFieldValue(solrDoc, "subject"));
        
        String statusStr = getStringFieldValue(solrDoc, "status");
        if (statusStr != null) {
            document.setStatus(LegalDocument.DocumentStatus.valueOf(statusStr));
        }
        
        Object decisionDateObj = solrDoc.getFieldValue("decision_date");
        if (decisionDateObj != null) {
            LocalDateTime decisionDate = parseDate(decisionDateObj);
            if (decisionDate != null) {
                document.setDecisionDate(decisionDate);
            }
        }
        
        Object crawledAtObj = solrDoc.getFieldValue("crawled_at");
        if (crawledAtObj != null) {
            LocalDateTime crawledAt = parseDate(crawledAtObj);
            if (crawledAt != null) {
                document.setCrawledAt(crawledAt);
            }
        }
        
        return document;
    }

    private List<LegalDocument> convertSolrDocumentList(SolrDocumentList solrDocs) {
        return solrDocs.stream()
            .map(this::convertFromSolrDocument)
            .collect(Collectors.toList());
    }

    private String escapeQueryValue(String value) {
        if (value == null) return "";
        // Escape special Solr characters
        return value.replaceAll("([\\\\+\\-!\\(\\){}\\[\\]^\"~*?:/])", "\\\\$1");
    }
    
    // ==================== SOLR-SPECIFIC METHODS ====================
    
    /**
     * Full-text search across all text fields
     */
    public List<LegalDocument> fullTextSearch(String searchTerm, int maxResults) {
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery(searchTerm);
            query.setParam("defType", "edismax");
            query.setParam("qf", "title^3.0 summary^2.0 full_text^1.0 case_number^4.0 ecli_identifier^4.0");
            query.setRows(maxResults);
            query.setHighlight(true);
            query.setHighlightSimplePre("<mark>");
            query.setHighlightSimplePost("</mark>");
            
            QueryResponse response = solrClient.query(query);
            return convertSolrDocumentList(response.getResults());
            
        } catch (Exception e) {
            logger.error("Full-text search failed for term: {}", searchTerm, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get suggestions for autocomplete
     */
    public List<String> getSuggestions(String term) {
        try {
            SolrQuery query = new SolrQuery();
            query.setRequestHandler("/suggest");
            query.set("suggest", true);
            query.set("suggest.q", term);
            query.set("suggest.count", 10);
            
            QueryResponse response = solrClient.query(query);
            // Process suggestions from response
            return Collections.emptyList(); // Simplified for brevity
            
        } catch (Exception e) {
            logger.error("Suggestions failed for term: {}", term, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Helper method to safely get String field values from SolrDocument
     * Handles both String values and ArrayList<String> values from Solr
     */
    private String getStringFieldValue(SolrDocument solrDoc, String fieldName) {
        Object fieldValue = solrDoc.getFieldValue(fieldName);
        if (fieldValue == null) {
            return null;
        }
        
        if (fieldValue instanceof String) {
            return (String) fieldValue;
        } else if (fieldValue instanceof java.util.Collection) {
            // Handle multivalued fields - take the first value
            java.util.Collection<?> collection = (java.util.Collection<?>) fieldValue;
            if (!collection.isEmpty()) {
                Object firstValue = collection.iterator().next();
                return firstValue != null ? firstValue.toString() : null;
            }
            return null;
        } else {
            // Convert other types to string
            return fieldValue.toString();
        }
    }
}