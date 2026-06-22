package com.kapil.typeahead.service;

import com.kapil.typeahead.dto.SearchResultsResponse;
import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final BatchWriterService batchWriterService;
    private final SearchQueryRepository searchQueryRepository;

    public void submitSearch(String query) {

        String normalizedQuery = normalizeQuery(query);

        batchWriterService.addToBatch(normalizedQuery, 1);
    }

    public String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }

        return query.trim().toLowerCase();
    }

    public SearchResultsResponse searchResults(String query) {
        String normalizedQuery = normalizeQuery(query);

        List<SearchResultsResponse.SearchResultItem> results = searchQueryRepository
                .findTop10ByQueryTextContainingIgnoreCaseOrderByCountDesc(normalizedQuery)
                .stream()
                .map(this::toResultItem)
                .toList();

        return new SearchResultsResponse(normalizedQuery, results);
    }

    private SearchResultsResponse.SearchResultItem toResultItem(SearchQuery searchQuery) {
        long count = searchQuery.getCount() != null ? searchQuery.getCount() : 0L;
        return new SearchResultsResponse.SearchResultItem(searchQuery.getQueryText(), count);
    }
}
