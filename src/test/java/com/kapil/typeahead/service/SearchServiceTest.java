package com.kapil.typeahead.service;

import com.kapil.typeahead.dto.SearchResultsResponse;
import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private BatchWriterService batchWriterService;

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @InjectMocks
    private SearchService searchService;

    @Test
    void submitSearchNormalizesQueryBeforeBatching() {
        searchService.submitSearch("  Java Tutorial  ");

        verify(batchWriterService).addToBatch("java tutorial", 1);
    }

    @Test
    void submitSearchRejectsBlankQuery() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> searchService.submitSearch("   ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void searchResultsReturnsDatabaseMatches() {
        SearchQuery query = new SearchQuery(
                1L,
                "java tutorial",
                42L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(searchQueryRepository.findTop10ByQueryTextContainingIgnoreCaseOrderByCountDesc("java"))
                .thenReturn(List.of(query));

        SearchResultsResponse response = searchService.searchResults(" Java ");

        assertEquals("java", response.query());
        assertEquals(1, response.results().size());
        assertEquals("java tutorial", response.results().getFirst().text());
        assertEquals(42L, response.results().getFirst().count());
    }
}
