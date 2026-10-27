package com.urlshortener;

import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.repository.ShortenedUrlRepository;
import com.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class UrlShortenerServiceTests {

    private static final String BASE_URL = "http://localhost:8080";
    private UrlShortenerService service;
    private static final int GENERATED_ALIAS_LENGTH = 7;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var repository = new ShortenedUrlRepository(jdbcTemplate);
        repository.init();
        service = new UrlShortenerService(repository);
    }

    @Test
    void shorten_WithValidUrl_ReturnsShortUrlResponse() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");

        ShortenUrlResponse response = service.shorten(request, BASE_URL);

        assertNotNull(response.getAlias());
        assertEquals("https://example.com/test", response.getFullUrl());
        assertTrue(response.getShortUrl().startsWith(BASE_URL + "/"));
    }

    @Test
    void shorten_WithCustomAlias_ReturnsCustomAlias() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com");
        request.setCustomAlias("my-alias");

        ShortenUrlResponse response = service.shorten(request, BASE_URL);

        assertEquals("my-alias", response.getAlias());
        assertEquals(BASE_URL + "/my-alias", response.getShortUrl());
    }

    @Test
    void getFullUrl_ReturnsExistingUrl() throws ExecutionException, InterruptedException {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com");
        request.setCustomAlias("example");
        service.shorten(request, BASE_URL);

        String fullUrl = CompletableFuture.supplyAsync(() -> service.getFullUrl("example")).get();

        assertEquals("https://example.com", fullUrl);
    }
    
    @Test
    void generateAlias_ReturnsGeneratedAlias() {
    	String alias = service.generateAlias();
        //assert that an alias is returned
    	assertNotNull(alias);
    	assertNotEquals("", alias);
    	assertEquals(GENERATED_ALIAS_LENGTH, alias.length());
    }
    
    @Test
    void delete_ShouldDeleteAlias() throws ExecutionException, InterruptedException {

    	String alias = "my-alias";
    	final String fullUrlExpected = "https://example.com/";
    	
    	var request = new ShortenUrlRequest();
        request.setFullUrl(fullUrlExpected);
        request.setCustomAlias(alias);
        service.shorten(request, BASE_URL);
        
        String fullUrlActual = CompletableFuture.supplyAsync(() -> service.getFullUrl(alias)).get();
        assertEquals(fullUrlExpected, fullUrlActual);
        
        service.delete(alias);

        fullUrlActual = CompletableFuture.supplyAsync(() -> service.getFullUrl(alias)).get();
        assertNotEquals(fullUrlExpected, fullUrlActual);
    }
    
}
