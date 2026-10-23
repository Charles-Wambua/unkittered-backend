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

## Pet-first fields (migration V18)

Structured data for the `unkittered-ui` Flutter client. Older clients keep
working: every new field is additive and the legacy shapes are still filled in.

| Area | Contract |
|------|----------|
| Pets | `ProfileDto.animals[]`: `{id, name, species, breedAndAge, energy (1–5), getsAlongWith[], comesOnDates, photoUrl}`. Replace the whole list with `PUT /v1/me/profile {"animals":[…]}` (max 6). `species` ∈ `dog,cat,bird,reptile,other`; `getsAlongWith` ∈ `big_dogs,small_dogs,cats,new_people`. Legacy `pets[]` is derived (`"🐕 Biscuit"`); legacy writes of `pets` are converted. |
| Pledge | `PUT /v1/me/profile {"pledgePath":"child_free_by_choice\|sterilised\|pet_parent"}` sets the pledge and restarts the yearly clock. `ProfileDto` adds `pledgePath`, `pledgeRenewsAt`. |
| Badge | `ProfileDto.badge` ∈ `none, child_free_verified, sterilised_verified`, computed server-side. **`isVerified` is no longer accepted from clients** — only admin review grants it. |
| Chat | `POST /v1/conversations/{id}/messages {"kind", "text", "payload"}`. Kinds: `text` (`text`), `pet_note` (`fromPet,toPet,text`), `photo` (`url` from `/me/photos`, `caption`), `plan` (`title,place,startsAt,durationMinutes,amenities`; server assigns `id`), `plan_response` (`planId,accepted`; only the other person can answer). Every message keeps a readable `text`. `MessageDto` and the WebSocket push add `kind`, `payload`. `ConversationDto` adds `lastMessageKind`, `plan`, `planStatus`. Moderation covers every human-written field. |
| Packs | Meetups add `kind` (`walk,hike,picnic,cafe,games,volunteer`), `petPolicy` (`pets_welcome,dogs_only,humans_only`), `durationMinutes`, `petCount`, `bringingPet`. `POST /v1/meetups/{id}/rsvp` takes optional `{"withPet":true}`. A full pack returns **409**, except for Gold members, who skip the waitlist. `category` is derived from `kind` when omitted. |

## Filters, sterilisation, pause, deletion (migration V19)

| Area | Contract |
|------|----------|
| Discovery filters | `GET/PUT /v1/me/discovery-prefs {ageMin, ageMax (18–99), maxDistanceKm?, verifiedOnly, sterilisedOnly, hasPetsOnly, species[], interests[]}`. Saving evicts the cached deck; `DiscoveryFilter` is a pure, unit-tested predicate. |
| Sterilisation | `PUT /v1/me/profile {"sterilisationProcedure":"vasectomy\|bilateral_tubal_ligation\|salpingectomy\|hysterectomy\|other"}` (empty string clears). `ProfileDto.sterilisationStatus` ∈ `none, declared, verified`. Proof: `POST /v1/me/verification/sterilisation` (multipart image/PDF); admins review at `GET /v1/admin/sterilisation-proofs`, `POST …/{userId}/approve\|reject`. The document is deleted after review. |
| Pause | `PUT /v1/me/profile {"paused":true}` hides the member from every deck; chats stay open. |
| Interests | `PUT /v1/me/profile {"interests":[…]}` (platonic "looking for": hiking buddy, travel companion, chess partner…). |
| Meetups | `kind` adds `dinner`, `travel`. |
| Delete account | `DELETE /v1/me {"password","reason"}` → **204**; all data cascades. A wrong password is **403** (not 401, which clients treat as an expired session). |

## Accounts, money and extras (migrations V20–V23)

| Area | Contract |
|------|----------|
| Forgot password | `POST /v1/auth/forgot-password {email}` → always **202**; emails a 6-digit code (hashed, 15 min, 5 tries, 1/min). `POST /v1/auth/reset-password {email, code, newPassword}` → signs in. The old email-only reset is gone (it allowed account takeover). |
| Google / Apple | `POST /v1/auth/oauth {provider, idToken, displayName?}`: the ID token's signature, issuer, audience and expiry are verified; accounts link by the provider's subject. With no client ids configured the provider answers **503** (it used to create an account for anyone). |
| Allowance | `GET /v1/subscriptions/me` → `{tier, wagsLeftToday (null = unlimited), petNotesLeftThisWeek, boostsLeftThisMonth}`, read from the same counters that enforce the limits. |
| Who wagged | `GET /v1/likes/received`: Gold gets real profiles; everyone else gets anonymous teasers (no id, name or photo leaves the server). |
| Purchases | `POST /v1/subscriptions/verify` is refused (**503**) unless `UNKITTERED_BILLING_TRUST_CLIENT=true` — local testing only, until store receipt validation exists. |
| Paws wallet | `GET /v1/wallet/catalog`, `GET /v1/me/wallet`, `POST /v1/me/wallet/top-ups {packId, phone}` (M-Pesa STK push), `POST /v1/me/wallet/withdrawals {paws, phone}` (min 500 earned, 15% fee, B2C), `POST /v1/gifts {toProfileId, giftId}` (recipient keeps 50%; matched pairs see a server-made `gift` chat message). Wallets are row-locked in id order; M-Pesa results apply once. |
| M-Pesa | `UNKITTERED_MPESA_MODE` = `disabled` (default, 503) / `dev` (LOCAL ONLY, settles instantly) / `daraja`. Daraja needs `..._CONSUMER_KEY`, `..._CONSUMER_SECRET`, `..._SHORTCODE`, `..._PASSKEY`, `..._CALLBACK_BASE_URL` (public https), `..._CALLBACK_TOKEN` (long random secret), and for payouts `..._INITIATOR_NAME`, `..._SECURITY_CREDENTIAL`. Callbacks: `/v1/payments/mpesa/{token}/stk`, `/b2c-result`, `/b2c-timeout`. |
| Date Concierge | `POST /v1/conversations/{matchId}/date-plans` → `{plans, more}`: ideas from shared interests, pets and area (Gold sees three). |
| Location & map | `PUT /v1/me/location {latitude, longitude}` (stored to ~100 m). `GET /v1/discover/map` → people from your deck on a ~2 km grid with a fixed per-person offset; hidden-distance members never appear. |
| Reels / stories | Posting a reel is Plus & Gold (**403** otherwise). Story text is moderated and capped at 220 characters. |
| Species | `rabbit`, `fish`, `horse`, `small_pet` join dog / cat / bird / reptile / other. |

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

## Deploying

Production runs on the GCP VM alongside commerce-orchestrator: native
services, systemd and nginx, deployed by GitHub Actions or `sudo
unkittered-deploy`. See **[deploy/README.md](deploy/README.md)**.

## Smoke test

```bash
# Start the API with the local-testing switches the smoke test exercises:
UNKITTERED_MPESA_MODE=dev UNKITTERED_BILLING_TRUST_CLIENT=true \
UNKITTERED_LOG_RESET_CODES=true mvn spring-boot:run > /tmp/unkittered-api.log 2>&1 &

./scripts/smoke-test.sh          # BASE defaults to http://localhost:8081/v1
```

Never set those three switches in production.
