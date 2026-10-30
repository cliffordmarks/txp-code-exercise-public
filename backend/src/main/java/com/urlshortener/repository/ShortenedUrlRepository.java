package com.urlshortener.repository;

import com.urlshortener.model.UrlListItem;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ShortenedUrlRepository {

    private final JdbcTemplate jdbc;

    public ShortenedUrlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS shortened_urls ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "alias TEXT NOT NULL UNIQUE, "
                + "full_url TEXT NOT NULL, "
                + "created_at TEXT NOT NULL"
                + ")");
    }

    public boolean existsByAlias(String alias) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM shortened_urls WHERE alias = ?",
                Integer.class,
                alias);
        return count != null && count > 0;
    }

    public void save(String alias, String fullUrl, Instant createdAt) {
        jdbc.update("INSERT INTO shortened_urls (alias, full_url, created_at) VALUES (?, ?, ?)",
                alias, fullUrl, createdAt.toString());
    }

    public Optional<String> findFullUrlByAlias(String alias) {
        var results = jdbc.query("SELECT full_url FROM shortened_urls WHERE alias = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("full_url"),
                alias);
        return results.stream().findFirst();
    }

    /**
     * Returns all stored URLs in  order of creation date
     *
     * @param baseUrl prefix used to build each short URL, without a trailing slash
     */
    public List<UrlListItem> findAll(String baseUrl) {
        return jdbc.query("SELECT alias, full_url FROM shortened_urls ORDER BY created_at DESC, id DESC",
                urlListItemMapper(baseUrl));
    }
    
    /**
     * Returns one page of stored URLs, newest first - ordered by creation date/time, then by ID.
     *
     * <p>The limit and offset are passed straight to the query without validation; callers
     * are expected to clamp them first, as {@code UrlShortenerService.getPage} does.
     *
     * @param baseUrl prefix used to build each short URL, without a trailing slash
     * @param limit   maximum number of rows to return
     * @param offset  number of rows to skip, as a row count and not a page number
     *                (for page {@code n} of size {@code s}, pass {@code n * s})
     * @return the requested subset of URLs within the bounds; empty if the offset is past the last row
     */
    public List<UrlListItem> findPage(String baseUrl, int limit, long offset) {
        return jdbc.query(
                "SELECT alias, full_url FROM shortened_urls ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                urlListItemMapper(baseUrl), limit, offset);
    }

    /**
     * Returns the total number of stored URLs.
     *
     * @return the row count, or {@code 0} if the table is empty
     */
    public long count() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM shortened_urls", Long.class);
        return total == null ? 0 : total;
    }    

    public boolean deleteByAlias(String alias) {
        var rows = jdbc.update("DELETE FROM shortened_urls WHERE alias = ?", alias);
        return rows > 0;
    }

    private RowMapper<UrlListItem> urlListItemMapper(String baseUrl) {
        return (ResultSet rs, int rowNum) -> new UrlListItem(
                rs.getString("alias"),
                rs.getString("full_url"),
                baseUrl + "/" + rs.getString("alias")
        );
    }
}
