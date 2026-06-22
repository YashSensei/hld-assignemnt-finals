package com.kapil.typeahead.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private BatchWriterService batchWriterService;

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
}
