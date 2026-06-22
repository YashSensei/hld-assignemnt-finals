package com.kapil.typeahead.dto;

import java.util.List;

public record SearchResultsResponse(
        String query,
        List<SearchResultItem> results
) {
    public record SearchResultItem(
            String text,
            long count
    ) {}
}
