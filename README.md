# elderhive-be

ElderHive backend — Spring Boot 3.5.x, Java 21, package root `com.elderhive`.

See the root-level `README.md` and `IMPLEMENTATION_PLAN.md` one directory up
(`~/SakshamSingla/elderhive/`) for full project context — this is one of two
repos (the other being `elderhive-fe`) that make up the ElderHive project.

## Local dev

```bash
# from the parent elderhive/ directory
docker compose up -d   # starts Postgres on localhost:5435

# from this directory
mvn spring-boot:run    # boots on http://localhost:8080
```

See `.env.example` for overridable config (DB credentials, JWT secret, CORS origin).
