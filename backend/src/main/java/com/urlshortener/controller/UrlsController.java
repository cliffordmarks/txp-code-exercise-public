package com.urlshortener.controller;

import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.model.UrlListItem;
import com.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@org.springframework.web.bind.annotation.CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class UrlsController {

    private final UrlShortenerService service;

    public UrlsController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shorten(
            @Valid @RequestBody ShortenUrlRequest request,
            HttpServletRequest requestContext) {
    	
    	String baseUrl = getBaseUrl(requestContext);
    	
    	ShortenUrlResponse response = service.shorten(request, baseUrl);
    	
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    
    /**
     * Lists shortened URLs, newest first.
     *
     * <p>If {@code size} is omitted, all URLs are returned. If it is provided, only the
     * requested zero-based {@code page} of that size is returned. In both cases the total
     * number of stored URLs is sent in the {@code X-Total-Count} response header.
     *
     * @param page           zero-based page number; ignored when {@code size} is not provided
     * @param size           page size (restricted to 1-100), or {@code null} to return everything
     * @param requestContext the incoming request, used to build each short URL
     * @return {@code 200 OK} with a JSON array of URLs, or an empty list.
     */
    @GetMapping("/urls")
    public ResponseEntity<List<UrlListItem>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest requestContext) {
        var baseUrl = getBaseUrl(requestContext);
        var body = (size == null)
                ? service.getAll(baseUrl)                 // no paging requested: current behaviour
                : service.getPage(baseUrl, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(service.count()))
                .body(body);
    }
    
    @GetMapping("/{alias}")
    public ResponseEntity<Void> redirectToUrl(@PathVariable String alias) {
        var fullUrl = service.getFullUrl(alias);
        if (fullUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(fullUrl)).build();
    }

    @DeleteMapping("/{alias}")
    public ResponseEntity<?> delete(@PathVariable String alias) {
    	
        if (!service.delete(alias)) {
        	//send correct structure back and text (which satisfies test)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No URL found for alias '" + alias + "'."));
        }
        return ResponseEntity.noContent().build();
    }

    private String getBaseUrl(HttpServletRequest request) {
        var scheme = request.getScheme();
        var serverName = request.getServerName();
        var serverPort = request.getServerPort();
        var baseUrl = new StringBuilder(scheme).append("://").append(serverName);
        if (serverPort != 80 && serverPort != 443) {
            baseUrl.append(":").append(serverPort);
        }
        return baseUrl.toString();
    }
}
