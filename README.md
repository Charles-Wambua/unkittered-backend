# Unkittered API

Backend for the **Unkittered** dating app (Flutter client in `../date_mate`).

A **modular monolith** built with Spring Boot. One deployable service, clean
internal modules (`auth`, `user`, `profile`, `discover`, `interaction`,
`messaging`) so any module can be split into a microservice later without
changing callers.

## Stack

| Concern         | Tech                                            |
|-----------------|-------------------------------------------------|
| Language        | Java 21                                         |
| Framework       | Spring Boot 3.4 (Web, Data JPA, Security)       |
| Database        | PostgreSQL 16 + Flyway migrations               |
| Cache / limits  | Redis 7 (discover deck cache, daily-like quota) |
| Async events    | AWS SQS (LocalStack locally) via spring-cloud-aws |
| Auth            | Stateless JWT (`Authorization: Bearer <token>`) |
| API docs        | springdoc / Swagger UI                          |

## Architecture at a glance

```
Flutter app ──HTTP──▶ AuthController ─┐
                      DiscoverController ├─▶ Services ─▶ Postgres (source of truth)
                      InteractionController ┘        └─▶ Redis  (deck cache, quotas)
                                                       │
                              mutual like ─▶ MatchEventPublisher ─▶ SQS ─▶ MatchEventConsumer
                                                                            (push, analytics…)
```

## Endpoints (slice 1)

| Method | Path                 | Auth | Body / Returns                          |
|--------|----------------------|------|-----------------------------------------|
| POST   | `/v1/auth/register`  | no   | `{email,password,displayName}` → `{token,user}` |
| POST   | `/v1/auth/login`     | no   | `{email,password}` → `{token,user}`     |
| GET    | `/v1/discover`       | yes  | → `{profiles:[…]}`                      |
| POST   | `/v1/likes`          | yes  | `{profileId}` → `{isMatch}`             |
| GET    | `/v1/likes/received` | yes  | → `{profiles:[…]}`                      |

Also wired: `POST /v1/super-likes`, `POST /v1/passes`.

## Endpoints (slice 2 — full client coverage)

| Method | Path                                      | Auth | Body / Returns                                |
|--------|-------------------------------------------|------|-----------------------------------------------|
| POST   | `/v1/auth/oauth`                          | no   | `{provider}` → `{token,user}`                 |
| POST   | `/v1/auth/logout`                         | no   | → `204`                                       |
| GET    | `/v1/me`                                  | yes  | → `{…user}`                                   |
| GET    | `/v1/me/profile`                          | yes  | → `{…profile}`                                |
| PUT    | `/v1/me/profile`                          | yes  | `{…partial profile}` → `{…profile}`           |
| POST   | `/v1/me/onboarding/complete`              | yes  | → `{…user}`                                   |
| POST   | `/v1/me/photos`                           | yes  | multipart `file` → `{url}`                     |
| GET    | `/v1/matches`                             | yes  | → `{matches:[{id,profile,createdAt}]}`        |
| GET    | `/v1/conversations`                       | yes  | → `{conversations:[{matchId,profile,…}]}`     |
| GET    | `/v1/conversations/{matchId}/messages`    | yes  | → `{messages:[{id,isMe,text,…}]}` (marks read)|
| POST   | `/v1/conversations/{matchId}/messages`    | yes  | `{text}` → `{…message}`                       |
| GET    | `/v1/subscriptions/me`                    | yes  | → `{tier}`                                    |
| POST   | `/v1/subscriptions/verify`                | yes  | `{productId}` → `{tier}`                       |
| POST   | `/v1/subscriptions/cancel`                | yes  | → `204`                                       |
| POST   | `/v1/blocks`                              | yes  | `{profileId}` → `204` (block; drops the match)|
| DELETE | `/v1/blocks/{profileId}`                  | yes  | → `204` (unblock)                              |
| GET    | `/v1/blocks`                              | yes  | → `{profiles:[…]}` (blocked users)             |
| POST   | `/v1/reports`                             | yes  | `{profileId,reason,details?}` → `204`          |
| DELETE | `/v1/matches/{matchId}`                   | yes  | → `204` (unmatch)                              |

These match the Flutter `RemoteAuthSource` / `RemoteProfileSource` /
`RemoteChatSource` / `RemoteMeSource` / `RemoteSubscriptionSource` contracts, so
the app runs fully against the backend by flipping `useMockData` to `false`
(the Flutter `apiBaseUrl` already defaults to `http://localhost:8080/v1`):

```
flutter run --dart-define=USE_MOCK_DATA=false \
            --dart-define=API_BASE_URL=http://localhost:8080/v1
```

Real-time chat is delivered over a WebSocket at `ws://<host>/ws/chat?token=<jwt>`
(message + match events pushed live).

## File storage (self-hosting friendly)

Photo upload is provider-pluggable via `unkittered.storage.provider`:

| Provider        | Behaviour                                                              |
|-----------------|------------------------------------------------------------------------|
| `local` (default) | Writes to `unkittered.storage.local.dir` (default `./uploads`), served at `/files/**`. |
| `s3`            | Reserved extension point — not yet wired.                               |

When self-hosting, point `UNKITTERED_PUBLIC_BASE_URL` at the server's reachable
address (a phone can't load `localhost`), e.g.:

```
UNKITTERED_STORAGE_DIR=/var/unkittered/uploads \
UNKITTERED_PUBLIC_BASE_URL=http://192.168.1.50:8080 \
./run.sh
```

## Run locally

```bash
# 1. Start Postgres, Redis, LocalStack (creates the SQS queue automatically)
docker compose up -d

# 2. Run the API (JDK 21)
./run.sh         # or: mvn spring-boot:run

# 3. Explore
open http://localhost:8080/swagger-ui.html
```

## Smoke test

```bash
./scripts/smoke-test.sh
```
