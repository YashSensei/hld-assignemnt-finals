package com.kapil.typeahead.service;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchWriterService {

    private final SearchStore searchStore;
    private final Trie trie;
    private final ConsistentHashingService consistentHashingService;
    private final SearchQueryRepository searchQueryRepository;
    private final MetricsService metricsService;
    private final PlatformTransactionManager transactionManager;

    private final Map<String, Long> batchBuffer = new ConcurrentHashMap<>();
    private final AtomicLong bufferSize = new AtomicLong(0);
    private final Object bufferLock = new Object();

    private static final int FLUSH_THRESHOLD = 1000;
    private static final String CACHE_PREFIX = "suggest:";

    public void addToBatch(String query, long delta) {
        metricsService.recordSearchRequest(delta);

        long size;
        synchronized (bufferLock) {
            batchBuffer.merge(query, delta, Long::sum);
            size = bufferSize.addAndGet(delta);
        }

        if (size >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFlush() {

        if (getBufferSize() > 0) {
            flush();
        }
    }

    public synchronized void flush() {

        Map<String, Long> batchSnapshot = drainBatch();

        if (batchSnapshot.isEmpty()) {
            return;
        }

        log.info("Flushing batch buffer with {} entries", batchSnapshot.size());

        try {
            persistBatch(batchSnapshot);
        } catch (RuntimeException ex) {
            restoreBatch(batchSnapshot);
            throw ex;
        }

        for (Map.Entry<String, Long> entry : batchSnapshot.entrySet()) {
            String query = entry.getKey();
            Long delta = entry.getValue();

            trie.insert(query);
            searchStore.increment(query, delta);
            searchStore.incrementRecent(query, delta);
        }

        metricsService.recordDbWrites(batchSnapshot.size());

        invalidateCacheForQueries(batchSnapshot.keySet());

        log.info("Batch flush completed");
    }

    private Map<String, Long> drainBatch() {
        synchronized (bufferLock) {
            if (batchBuffer.isEmpty()) {
                return Map.of();
            }

            Map<String, Long> batchSnapshot = new HashMap<>(batchBuffer);
            batchBuffer.clear();
            bufferSize.set(0);
            return batchSnapshot;
        }
    }

    private void restoreBatch(Map<String, Long> batchSnapshot) {
        synchronized (bufferLock) {
            for (Map.Entry<String, Long> entry : batchSnapshot.entrySet()) {
                batchBuffer.merge(entry.getKey(), entry.getValue(), Long::sum);
                bufferSize.addAndGet(entry.getValue());
            }
        }
    }

    private void persistBatch(Map<String, Long> batchSnapshot) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            for (Map.Entry<String, Long> entry : batchSnapshot.entrySet()) {
                String query = entry.getKey();
                Long delta = entry.getValue();

                SearchQuery searchQuery = searchQueryRepository.findByQueryText(query)
                        .orElse(new SearchQuery(null, query, 0L, LocalDateTime.now(), LocalDateTime.now()));

                long existingCount = searchQuery.getCount() != null ? searchQuery.getCount() : 0L;
                searchQuery.setCount(existingCount + delta);
                searchQuery.setLastUpdated(LocalDateTime.now());
                searchQuery.setLastSearchedAt(LocalDateTime.now());

                searchQueryRepository.save(searchQuery);
            }
        });
    }

    private void invalidateCacheForQueries(Set<String> queries) {

        Set<String> keysToDelete = queries.stream()
                .flatMap(query -> generatePrefixKeys(query).stream())
                .collect(Collectors.toSet());

        if (!keysToDelete.isEmpty()) {
            Map<StringRedisTemplate, Set<String>> keysByTemplate = new HashMap<>();
            for (String key : keysToDelete) {
                StringRedisTemplate template = consistentHashingService.getTemplate(key);
                if (template != null) {
                    keysByTemplate.computeIfAbsent(template, t -> new HashSet<>()).add(key);
                }
            }

            for (Map.Entry<StringRedisTemplate, Set<String>> entry : keysByTemplate.entrySet()) {
                try {
                    entry.getKey().delete(entry.getValue());
                    log.info("Invalidated {} cache keys on a consistent hashing node", entry.getValue().size());
                } catch (RuntimeException ex) {
                    log.warn("Failed to invalidate {} cache keys on a consistent hashing node", entry.getValue().size(), ex);
                }
            }
        }
    }

    private Set<String> generatePrefixKeys(String query) {

        return IntStream.rangeClosed(1, query.length())
                .mapToObj(i -> CACHE_PREFIX + query.substring(0, i))
                .collect(Collectors.toSet());
    }

    public long getBufferSize() {
        return bufferSize.get();
    }
}
