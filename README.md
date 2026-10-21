# QueueLess — virtual queue platform with live, self-correcting wait times

Customers join a queue from their phone (no account), get a ticket, and watch their position and
ETA update in real time. Business owners run the line from a dashboard.

**Stack:** Java 21 · Spring Boot 3.3 · Spring Security (stateless JWT) · Spring Data JPA ·
STOMP over WebSocket · H2 (dev) / PostgreSQL (prod) · vanilla JS frontend served by Spring Boot · Docker

## What makes it more than a CRUD app

| Concern | How it is handled |
|---|---|
| **Concurrency** | Ticket numbering and queue advancement take a `PESSIMISTIC_WRITE` lock on the shop row, so two simultaneous joins can never get the same number and a cancel can't race with "call next". |
| **Adaptive ETA** | `EtaCalculator` keeps an exponential moving average of real service times (α = 0.3, clamped to 30 s – 1 h). Estimates get more accurate the longer a shop runs. Pure class, fully unit tested. |
| **Real-time** | State changes publish a Spring event; a `@TransactionalEventListener(AFTER_COMMIT)` pushes the new public status to `/topic/shops/{slug}`, so clients never see data from a rolled-back transaction. Pages also poll every 15 s as a fallback. |
| **Auth & isolation** | BCrypt passwords, JWT bearer tokens, `/api/owner/**` protected. Accessing another owner's queue returns 404 (not 403) so ids can't be probed. |
| **Safe by default** | Bean Validation on all input; the frontend never uses `innerHTML` for user text; ticket tokens are random, not sequential ids. |

## Run locally (H2 in memory)

```bash
mvn spring-boot:run
# open http://localhost:8080
```

1. Go to **/owner.html**, create an account, create a queue (e.g. "Chai Corner").
2. Open the customer link / QR in another tab (or phone on the same network) and join.
3. Press **Call next** on the dashboard and watch the customer page react instantly.

## Tests

```bash
mvn test
```

- `EtaCalculatorTest` — EMA, clamping, ETA maths
- `QueueFlowIntegrationTest` — register → create queue → join → call next → done, cancel rules,
  closed-queue rejection, auth and cross-owner isolation

## API summary

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | – | Get a JWT |
| GET/POST | `/api/owner/shops` | owner | List / create queues |
| GET | `/api/owner/shops/{id}/queue` | owner | Dashboard snapshot |
| POST | `/api/owner/shops/{id}/next` · `/skip` · `/toggle-open` | owner | Run the queue |
| GET | `/api/public/shops/{slug}` | – | Public status + wait for a new joiner |
| POST | `/api/public/shops/{slug}/join` | – | Take a ticket |
| GET / DELETE | `/api/public/tickets/{token}` | – | Ticket status / leave the queue |
| WS | `/ws` → subscribe `/topic/shops/{slug}` | – | Live status pushes |

## Deploy

### Docker Compose (app + PostgreSQL)

```bash
docker compose up --build
# http://localhost:8080
```

Change `APP_JWT_SECRET` in `docker-compose.yml` first.

### Render (free tier, one click)

1. Push this folder to a GitHub repo.
2. In Render: **New → Blueprint** → select the repo. `render.yaml` provisions the web service and a
   PostgreSQL database and generates `APP_JWT_SECRET` for you.
3. Open the URL Render gives you. Health check: `/actuator/health`.

Any platform that runs a Dockerfile (Railway, Fly.io, Google Cloud Run, AWS App Runner) works too.
Set `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `APP_JWT_SECRET`
(and `DB_PORT` if not 5432).

## Ideas to extend it (good interview talking points)

- Replace `ddl-auto: update` with **Flyway** migrations.
- Add **SMS/WhatsApp/email "you're next"** notifications via a notifier interface.
- Add **rate limiting** on the public join endpoint (e.g. Bucket4j) to stop queue spam.
- Per-day analytics for owners (peak hours, average wait, no-show rate).
- Multi-counter queues (several staff serving one line).
- Testcontainers-based tests against real PostgreSQL.

## Project layout

```
src/main/java/com/queueless
  config/    SecurityConfig, WebSocketConfig
  security/  JwtService, JwtAuthFilter
  domain/    Owner, Shop, Ticket, TicketStatus
  repo/      Spring Data repositories (incl. locking queries)
  service/   QueueService (core logic), EtaCalculator, AuthService, QueueBroadcaster
  web/       Controllers, DTO records, exception handling
src/main/resources/static   index / join / ticket / owner pages
```
