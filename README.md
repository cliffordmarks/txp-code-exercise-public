# URL Shortener

A small full-stack URL shortener. Paste a long URL, optionally choose a custom alias, and get a short link that redirects to the original. Created links are listed newest first, with paging, and can be deleted.

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2 (web, JDBC, validation), SQLite |
| Frontend | React 18, TypeScript, Vite, Vitest and Testing Library |
| Serving | nginx (static files and reverse proxy to the API) |
| Packaging | Docker and Docker Compose |

## Project structure

```
.
├── backend/                 Spring Boot API
│   └── src/main/java/com/urlshortener/
│       ├── controller/      REST endpoints
│       ├── service/         alias generation, validation, business rules
│       ├── repository/      SQLite access via JdbcTemplate
│       ├── model/           request/response types
│       └── exception/       JSON error handling
├── frontend/                React single-page app
│   └── src/
│       ├── components/      ShortenForm, ResultBanner, UrlTable, Pager
│       ├── hooks/           useUrlShortener (state, paging, API calls)
│       └── services/        API client
└── docker-compose.yml
```

## Running with Docker

```bash
docker compose up --build
```

Then open **http://localhost:8080**.

- nginx serves the built React app and forwards `/shorten`, `/urls` and short-link paths such as `/abc1234` to the backend over the Compose network.
- The SQLite database lives in a Docker volume mounted at `/data`, so links survive container restarts.
- Rebuild with `--build` after changing `nginx.conf` or any source code, because both are baked into the images.

## Local development
The dev proxy also forwards single-segment alias paths, so short-link redirects and deletes work on port 3000 too.
**Backend** (requires Java 17 and Maven):

```bash
cd backend
mvn spring-boot:run
mvn test
```

The API listens on port 8080 by default and creates `urlshortener.db` in the working directory.

**Frontend** (requires Node 20):

```bash
cd frontend
npm install
npm run dev      # http://localhost:3000
npm test         # run the tests once
```

The Vite dev server proxies `/shorten` and `/urls` to `http://localhost:8040`, so the backend must be reachable on that port (for example, the Compose backend published as `8040:8080`). Short-link redirects and deletes use single-segment paths, which the dev proxy does not forward, so use the Docker setup to try those end to end.

## API

| Method | Path | Description | Success | Errors |
|---|---|---|---|---|
| `POST` | `/shorten` | Create a short URL | `201` with `{ shortUrl, alias, fullUrl }` | `400` invalid input, `409` alias taken |
| `GET` | `/urls` | List URLs, newest first | `200` with a JSON array | |
| `GET` | `/{alias}` | Redirect to the original URL | `302` with `Location` header | `404` unknown alias |
| `DELETE` | `/{alias}` | Delete a short URL | `204` | `404` unknown alias |

**Create request body**

```json
{ "fullUrl": "https://example.com/some/long/path", "customAlias": "my-link" }
```

`customAlias` is optional. If omitted, a random 7-character alias is generated.

**Paging.** `GET /urls` returns everything by default. Add `?page=0&size=10` to get one page (zero-based; `size` is clamped to 1-50). The total number of stored URLs is returned in the `X-Total-Count` response header.

**Errors** use a single JSON shape: `{ "error": "message" }`.

## Rules and behaviour

- `fullUrl` must be an absolute `http` or `https` URL with a host.
- Aliases are 2-64 characters of ASCII letters, digits and hyphens.
- `urls` and `shorten` are reserved because they clash with API paths. Add any single-segment routes your frontend uses to `RESERVED_ALIASES` in `UrlShortenerService`.
- Alias uniqueness is enforced twice: in the service under a lock, and by a `UNIQUE` constraint on the database column.
- Short links are built from the incoming request's `Host` header, which nginx forwards, so they come out as `http://localhost:8080/<alias>` when running under Docker.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Port the backend listens on |
| `DATABASE_PATH` | `./urlshortener.db` (`/data/urlshortener.db` in Docker) | SQLite file location |

Run only one backend instance, since SQLite uses a local file.
