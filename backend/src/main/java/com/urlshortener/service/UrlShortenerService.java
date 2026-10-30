package com.urlshortener.service;

import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.model.UrlListItem;
import com.urlshortener.repository.ShortenedUrlRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Business logic for creating, resolving, listing and deleting shortened URLs.
 *
 * <p>Aliases are either supplied by the caller or generated randomly. They are limited
 * to ASCII letters, digits and hyphens (2-64 characters) so they match the alias route
 * in the reverse proxy, and a few names are reserved because they collide with API paths.
 */
@Service
public class UrlShortenerService {

    private static final String ALIAS_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    private static final Set<String> RESERVED_ALIASES = Set.of("urls", "shorten"); /// add other routes in the front end app that should be reserved, e.g. "about", "login"

    private static final int GENERATED_ALIAS_LENGTH = 7;
    private static final int MAX_ASCII_CHARACTER = 128;
    private static final int MAX_PAGE_SIZE = 50;
    
    private final ShortenedUrlRepository repository;
    private final Object aliasLock = new Object();
    
    public UrlShortenerService(ShortenedUrlRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a shortened URL.
     *
     * <p>The full URL is validated and normalised first. If the request has no custom alias
     * (null or blank), a random one is generated; otherwise the trimmed custom alias is used.
     * The existence check and the save run within a synchronized block, so two concurrent requests in this
     * JVM cannot both save the same alias. The database's unique constraint on the alias
     * column is the backstop beyond that.
     *
     * @param request the full URL and an optional custom alias
     * @param baseUrl scheme, host and optional port used to build the short URL, without a
     *                trailing slash (for example {@code http://localhost:8080})
     * @return the short URL, its alias and the normalised full URL
     * @throws IllegalArgumentException if the full URL is missing or invalid, or the alias is
     *                                  malformed or reserved
     * @throws IllegalStateException    if the alias is already in use
     */
    public ShortenUrlResponse shorten(ShortenUrlRequest request, String baseUrl) {
        var fullUrl = normalizeUrl(request.getFullUrl());
        var alias = request.getCustomAlias() == null || request.getCustomAlias().isBlank()
                ? generateAlias()
                : request.getCustomAlias().trim();

        if (!isValidAlias(alias)) {
            throw new IllegalArgumentException("Alias may only contain letters, numbers, and hyphens (2–64 characters).");
        }
        //prevent reserved (url paths) from being used as aliases.
        if (isReserved(alias)) {
            throw new IllegalArgumentException("The alias '" + alias + "' is reserved.");
        }
        
        //need to prevent race condition where concurrent threads attempt to save same alias 
        synchronized (aliasLock) {
            if (repository.existsByAlias(alias)) {
                throw new IllegalStateException("The alias '" + alias + "' is already taken.");
            }
            repository.save(alias, fullUrl, Instant.now());
        }
                
        return new ShortenUrlResponse(baseUrl + "/" + alias, alias, fullUrl);
    }

    /**
     * Looks up the original URL for an alias.
     *
     * @param alias the alias to resolve
     * @return the full URL, or {@code null} if the alias does not exist
     */
    public String getFullUrl(String alias) {
        return repository.findFullUrlByAlias(alias).orElse(null);
    }

    /**
     * Returns every stored URL, with each short URL built from the given base URL.
     * 
     * @param baseUrl scheme, host and optional port, without a trailing slash
     * @return all shortened URLs; empty if none exist
     */
    public List<UrlListItem> getAll(String baseUrl) {
        return repository.findAll(baseUrl);
    }

    /**
     * Deletes the shortened URL with the given alias.
     *
     * @param alias the alias to delete
     * @return {@code true} if the alias existed and was removed, {@code false} if it does not exist
     */
    public boolean delete(String alias) {
    	//Search for the alias and if it does not exist then return false because no delete occurs.. 
    	if (repository.findFullUrlByAlias(alias).isEmpty()) {
            return false;
        }

    	//We got to here so alias must exist so delete it... 
        repository.deleteByAlias(alias);
        return true;
    }

    /**
     * Generates a random 7-character alias from letters and digits, retrying until the result
     * is neither reserved nor already in use.
     *
     * <p>This only checks availability and does not reserve the alias. In the unlikely event
     * that another request takes it first, {@link #shorten} rejects the duplicate.
     *
     * @return an unused, non-reserved alias
     */
    public String generateAlias() {
        String alias;
        do {
            var sb = new StringBuilder(GENERATED_ALIAS_LENGTH);
            for (int i = 0; i < GENERATED_ALIAS_LENGTH; i++) {
                sb.append(ALIAS_CHARS.charAt(ThreadLocalRandom.current().nextInt(ALIAS_CHARS.length())));
            }
            alias = sb.toString();
        } while (isReserved(alias) || repository.existsByAlias(alias));
        return alias;
    }

    /**
     * Returns {@code true} if the alias (compared case-insensitively) collides with an API path.
     * @param alias
     * @return
     */
    private static boolean isReserved(String alias) {
        return RESERVED_ALIASES.contains(alias.toLowerCase());
    }
    
    /**
     * Checks that an alias is within 2-64 characters long, digits or hyphens. ASCII-only
     * keeps it consistent with the pattern used by the nginx config alias route.
     */
    private static boolean isValidAlias(String alias) {
        if (alias == null || alias.length() < 2 || alias.length() > 64) {
            return false;
        }
        //Restrict to ASCII characters that match redirect server allowed chars.
        return alias.chars().allMatch(c -> c < MAX_ASCII_CHARACTER && (Character.isLetterOrDigit(c) || c == '-'));
    }

    /**
     * Validates and normalises a full URL. It must be non-blank, absolute, use http or https,
     * and have a host. Surrounding whitespace is trimmed and an empty path becomes {@code /}.
     * so a full url of http://example.com is returned as http://example.com/
     *
     * @param fullUrl the URL supplied by the caller
     * @return the normalised URL
     * @throws IllegalArgumentException if the URL is missing, malformed or uses another scheme
     */
    private static String normalizeUrl(String fullUrl) {
        if (fullUrl == null || fullUrl.isBlank()) {
            throw new IllegalArgumentException("fullUrl is required.");
        }

        try {
            var uri = new URI(fullUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("fullUrl must be a valid URL.");
            }

            var scheme = uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("fullUrl must be a valid URL.");
            }

            var path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }

            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("fullUrl must be a valid URL.");
        }
    }
    
    /**
     * Returns one page of URLs, newest first.
     *
     * <p>The page number is zero-based, and a negative value is treated as {@code 0}.
     * The page size is clamped to the range 1 to {@code MAX_PAGE_SIZE} (50), so an
     * oversized or non-positive request cannot cause an unbounded query.
     *
     * @param baseUrl scheme, host and optional port, without a trailing slash
     * @param page    zero-based page number
     * @param size    requested page size
     * @return the URLs on that page; empty if the page is past the end
     */
    public List<UrlListItem> getPage(String baseUrl, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) Math.max(page, 0) * safeSize;
        return repository.findPage(baseUrl, safeSize, offset);
    }

    /**
     * Returns the total number of stored URLs
     *
     * @return the total count
     */
    public long count() {
        return repository.count();
    }
}
