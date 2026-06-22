package com.kapil.typeahead.service;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchWriterServiceTest {

    @Mock
    private ConsistentHashingService consistentHashingService;

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SearchStore searchStore;
    private Trie trie;
    private MetricsService metricsService;
    private BatchWriterService batchWriterService;

    @BeforeEach
    void setUp() {
        searchStore = new SearchStore();
        trie = new Trie();
        metricsService = new MetricsService();

        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        batchWriterService = new BatchWriterService(
                searchStore,
                trie,
                consistentHashingService,
                searchQueryRepository,
                metricsService,
                transactionManager
        );
    }

    @Test
    void flushAddsNewQueryToDatabaseStoreAndTrie() {
        when(searchQueryRepository.findByQueryText("new query")).thenReturn(Optional.empty());

        batchWriterService.addToBatch("new query", 1);
        batchWriterService.flush();

        ArgumentCaptor<SearchQuery> queryCaptor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchQueryRepository).save(queryCaptor.capture());

        SearchQuery savedQuery = queryCaptor.getValue();
        assertEquals("new query", savedQuery.getQueryText());
        assertEquals(1L, savedQuery.getCount());
        assertEquals(1L, searchStore.getCount("new query"));
        assertTrue(trie.search("new", 10).contains("new query"));
        assertEquals(0L, batchWriterService.getBufferSize());
    }

    @Test
    void flushRestoresBatchWhenDatabaseWriteFails() {
        when(searchQueryRepository.findByQueryText("durable query"))
                .thenThrow(new RuntimeException("database unavailable"));

        batchWriterService.addToBatch("durable query", 1);

        assertThrows(RuntimeException.class, () -> batchWriterService.flush());

        assertEquals(1L, batchWriterService.getBufferSize());
        assertEquals(0L, searchStore.getCount("durable query"));
        assertFalse(trie.search("durable", 10).contains("durable query"));
    }
}
