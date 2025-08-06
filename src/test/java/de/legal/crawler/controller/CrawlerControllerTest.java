package de.legal.crawler.controller;

import de.legal.crawler.service.CrawlerOrchestrationService;
import de.legal.crawler.service.DocumentDownloadService;
import de.legal.crawler.repository.LegalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for CrawlerController
 */
@WebMvcTest(CrawlerController.class)
class CrawlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CrawlerOrchestrationService orchestrationService;

    @MockBean
    private LegalDocumentRepository documentRepository;

    @MockBean
    private DocumentDownloadService downloadService;

    @Test
    void testGetCrawlerStatus() throws Exception {
        // Mock dependencies
        when(documentRepository.count()).thenReturn(100L);
        when(downloadService.getStorageStats()).thenReturn(
            new DocumentDownloadService.StorageStats(50L, 1024000L)
        );

        mockMvc.perform(get("/api/crawler/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDocuments").value(100))
            .andExpect(jsonPath("$.storageStats.fileCount").value(50));
    }

    @Test
    void testStartCrawlWithDate() throws Exception {
        mockMvc.perform(post("/api/crawler/crawl")
                .param("date", "2024-08-06")
                .param("forceUpdate", "true"))
            .andExpect(status().isOk())
            .andExpected(jsonPath("$.message").value("Crawling started successfully"))
            .andExpected(jsonPath("$.date").value("2024-08-06"));

        verify(orchestrationService).startCrawling(any(), eq(true));
    }

    @Test
    void testStartCrawlWithoutDate() throws Exception {
        mockMvc.perform(post("/api/crawler/crawl"))
            .andExpect(status().isOk())
            .andExpected(jsonPath("$.message").value("Crawling started successfully"));

        verify(orchestrationService).startCrawling(any(), eq(false));
    }

    @Test
    void testSearchDocuments() throws Exception {
        mockMvc.perform(get("/api/crawler/search")
                .param("query", "test"))
            .andExpect(status().isOk());

        verify(documentRepository).findByTitleContainingIgnoreCase("test");
    }

    @Test
    void testRetryFailedDocuments() throws Exception {
        when(orchestrationService.retryFailedDocuments())
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(5));

        mockMvc.perform(post("/api/crawler/retry-failed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentsRetried").value(5));
    }
}