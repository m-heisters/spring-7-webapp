## Database Setup

### Stack
- MySQL 8 (`mysql:latest`), started via **Spring Boot's Docker Compose integration** (`spring-boot-docker-compose`), not manually.
- Flyway for schema migrations, `spring.jpa.hibernate.ddl-auto=validate` (schema is Flyway-owned, Hibernate never mutates it).

### How the connection actually works
`compose.yaml` defines the `mysql` service:

```yaml
services:
  mysql:
    image: 'mysql:latest'
    environment:
      - 'MYSQL_DATABASE=mydatabase'
      - 'MYSQL_USER=myuser'
      - 'MYSQL_PASSWORD=secret'
      - 'MYSQL_ROOT_PASSWORD=verysecret'
    ports:
      - '3306'
```

With `spring.docker.compose.enabled=true` (set in `application-localmysql.properties`), Spring Boot auto-detects this service on startup, **starts the container itself**, and creates a `JdbcConnectionDetails` bean directly from the container's actual env vars and mapped port.

**This "service connection" takes priority over `spring.datasource.*` properties.** The `url`/`username`/`password` values in `application-localmysql.properties` (`restdb` / `restadmin`) are effectively dead config — they don't match `compose.yaml` (`mydatabase` / `root` / `verysecret`) and are silently ignored. Don't try to "fix" them into alignment; either remove them or treat `compose.yaml` as the single source of truth.

### Port mapping
`ports: - '3306'` has no fixed host port, so Docker assigns a **random** host port on each container creation (e.g. `32802`). Spring Boot picks this up automatically via the service connection, so the app always connects correctly — but this means you **cannot** reliably connect via a hardcoded `127.0.0.1:3306` in Workbench/CLI. Always check the actual mapping first:

```bash
docker compose ps
```

### Persistence / container lifecycle — the main gotcha
- `compose.yaml` declares **no named volume**, but the official `mysql` image bakes in `VOLUME /var/lib/mysql`, so an **anonymous volume** is created implicitly.
- Spring Boot's default Compose lifecycle management is `start-and-stop`: on JVM shutdown it **stops** the container, it does **not remove** it.
- A stopped-but-not-removed container keeps its anonymous volume. On next launch, Spring Boot (or `docker compose up`) **reuses** the same container + volume rather than creating a fresh one.
- **Consequence:** a failed migration, bad manual schema edit, or corrupted state persists silently across app restarts and even across days — it is not a fresh environment on every run, despite looking like ephemeral local dev.

### Checking / resetting state
```bash
# See what's actually running and its real port
docker compose ps

# Check Flyway's view of migration history directly
docker compose exec mysql mysql -uroot -pverysecret -e \
  "SELECT version, description, success FROM mydatabase.flyway_schema_history ORDER BY installed_rank;"

# Nuclear reset — removes container AND the anonymous volume (data is gone)
docker compose down -v
```

`docker compose down -v` is the only way to guarantee a truly clean schema on next launch. Plain `down` (or the app's default stop-on-exit) leaves the volume intact.

### Known issue: V4__add_category.sql drop order
The migration drops parent before child:

```sql
-- ❌ wrong — beer_category still FKs to category
drop table if exists category;
drop table if exists beer_category;
```

If `beer_category` already exists when this runs again (e.g. after a partial failure left it in place), `DROP TABLE category` fails with MySQL error 3730 (FK constraint violation). Since MySQL DDL isn't transactional, this fails the whole migration and Flyway marks V4 as `success = 0` — which then blocks every subsequent startup until manually repaired. Fix: drop child before parent.

```sql
drop table if exists beer_category;
drop table if exists category;
```

### If you see "Detected failed migration to version X" again
1. `docker compose ps` — confirm the container and real port.
2. Run the `flyway_schema_history` query above to see which version failed.
3. If it's disposable local data: `docker compose down -v`, fix the offending migration if it's a genuine SQL bug, relaunch.
4. If you need to preserve data: `DELETE FROM flyway_schema_history WHERE version = 'X' AND success = 0;`, then manually verify/reconcile whatever DDL partially applied before restarting.
