package com.kapil.typeahead.service;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.cache.RedisNode;
import com.kapil.typeahead.dto.SuggestResponse;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionService {

    private final Trie trie;
    private final SearchStore searchStore;
    private final ConsistentHashingService consistentHashingService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "suggest:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public SuggestResponse suggest(String prefix) {
        long startTime = System.nanoTime();
        boolean isHit = false;

        String normalizedPrefix = prefix == null ? "" : prefix.trim().toLowerCase();
        if (normalizedPrefix.isBlank()) {
            metricsService.recordSuggestRequest(false, System.nanoTime() - startTime);
            return new SuggestResponse(normalizedPrefix, List.of());
        }

        String cacheKey = CACHE_PREFIX + normalizedPrefix;

        StringRedisTemplate template = consistentHashingService.getTemplate(cacheKey);
        String cachedResult = template != null ? template.opsForValue().get(cacheKey) : null;

        List<String> sortedQueries;

        if (cachedResult != null) {
            isHit = true;
            RedisNode node = consistentHashingService.getNode(cacheKey);
            System.out.println("Cache HIT for: " + normalizedPrefix + " on node " + (node != null ? node.getName() : "unknown"));
            sortedQueries = parseCachedSuggestions(cachedResult);
        } else {
            System.out.println("Cache MISS for: " + normalizedPrefix);

            List<String> matches = trie.search(normalizedPrefix, 50);

            System.out.println("Matches found = " + matches.size());

            sortedQueries = matches.stream()
                    .sorted(Comparator.comparingDouble(searchStore::getTrendingScore).reversed())
                    .limit(10)
                    .toList();

            if (template != null) {
                template.opsForValue().set(
                        cacheKey,
                        serializeSuggestions(sortedQueries),
                        CACHE_TTL
                );
            }
        }

        List<SuggestResponse.SuggestionItem> suggestions = sortedQueries.stream()
                .map(q -> new SuggestResponse.SuggestionItem(q, searchStore.getCount(q)))
                .toList();

        long duration = System.nanoTime() - startTime;
        metricsService.recordSuggestRequest(isHit, duration);

        return new SuggestResponse(normalizedPrefix, suggestions);
    }

    private List<String> parseCachedSuggestions(String cachedResult) {
        if (cachedResult == null || cachedResult.isEmpty()) {
            return List.of();
        }

        if (!cachedResult.stripLeading().startsWith("[")) {
            return List.of(cachedResult.split(","));
        }

        try {
            return objectMapper.readValue(cachedResult, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse cached suggestions", ex);
            return List.of();
        }
    }

    private String serializeSuggestions(List<String> suggestions) {
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize suggestions for cache", ex);
            return "";
        }
    }
}
