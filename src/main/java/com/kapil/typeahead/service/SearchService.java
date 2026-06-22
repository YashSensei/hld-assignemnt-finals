package com.kapil.typeahead.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final BatchWriterService batchWriterService;

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
}
