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


@Service
public class UrlShortenerService {

    private static final String ALIAS_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int GENERATED_ALIAS_LENGTH = 7;
    private static final int MAX_ASCII_CHARACTER = 128;
    private static final Set<String> RESERVED_ALIASES = Set.of("urls", "shorten"); /// add other routes in the front end app that should be reserved, e.g. "about", "login"

    private final ShortenedUrlRepository repository;
    private final Object aliasLock = new Object();
    
    public UrlShortenerService(ShortenedUrlRepository repository) {
        this.repository = repository;
    }

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

    public String getFullUrl(String alias) {
        return repository.findFullUrlByAlias(alias).orElse(null);
    }

    public List<UrlListItem> getAll(String baseUrl) {
        return repository.findAll(baseUrl);
    }

    /**
     * TODO: write javadoc
     * @return
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
     * TODO: write javadoc
     * @return
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

    private static boolean isReserved(String alias) {
        return RESERVED_ALIASES.contains(alias.toLowerCase());
    }
    
    private static boolean isValidAlias(String alias) {
        if (alias == null || alias.length() < 2 || alias.length() > 64) {
            return false;
        }
        //Restrict to ASCII characters that match redirect server allowed chars.
        return alias.chars().allMatch(c -> c < MAX_ASCII_CHARACTER && (Character.isLetterOrDigit(c) || c == '-'));
    }

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
}
