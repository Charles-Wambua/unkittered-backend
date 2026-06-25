# Unkittered API - README

## 📂 What's in This Folder?

Complete API endpoint documentation for the **Unkittered Dating App Backend** (Java Spring Boot).

### Files Included

1. **ALL_ENDPOINTS.md** (Comprehensive)
   - Full endpoint reference with request/response examples
   - Organized by feature (Auth, Discovery, Chat, etc.)
   - Includes error codes, rate limits, authentication details
   - Best for: Integration development, understanding each endpoint

2. **MODULES.md** (Organized by Module)
   - 14 functional modules with endpoint lists
   - Integration checklist (Phase 1-7)
   - Endpoint counts and statistics
   - Best for: Planning, understanding app structure

3. **QUICK_REFERENCE.csv** (At-a-Glance)
   - Condensed table format (Method, Path, Auth, Status, Description)
   - Easy to import into tools
   - Best for: Quick lookups, spreadsheet imports

4. **ENDPOINTS.json** (Machine-Readable)
   - Structured JSON format
   - Organized by endpoint category
   - Includes request/response schemas
   - Best for: API tools, automation, Postman import

5. **README.md** (This File)
   - Quick start guide and file descriptions

---

## 🚀 Quick Start

### Authentication
All endpoints (except Auth) require JWT bearer token:
```
Authorization: Bearer <JWT_TOKEN>
```

Get a token:
```bash
POST /v1/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}

Response:
{
  "token": "eyJhbGc...",
  "user": { ... }
}
```

### Base URL
- **Local:** `http://localhost:8080/v1`
- **Production:** `https://api.unkittered.com/v1`

---

## 📊 API Statistics

| Metric | Count |
|--------|-------|
| **Total Endpoints** | 59 |
| **Public Endpoints** | 5 (Auth only) |
| **Authenticated Endpoints** | 42 |
| **Admin-Only Endpoints** | 12 |
| **WebSocket Connections** | 1 |
| **Modules** | 14 |

---

## 🔑 Key Endpoints (Most Used)

```
# Authentication
POST   /v1/auth/register          - New account
POST   /v1/auth/login             - Get JWT token

# Discovery & Matching
GET    /v1/discover               - Browse profiles
POST   /v1/likes                  - Like profile
GET    /v1/matches                - Get matches

# Chat
GET    /v1/conversations          - Get conversations
POST   /v1/conversations/{matchId}/messages - Send message
WS     /ws/chat?token=<JWT>       - Real-time chat

# Profile
GET    /v1/me/profile             - Get user profile
PUT    /v1/me/profile             - Update profile
POST   /v1/me/photos              - Upload photo

# Safety
POST   /v1/blocks                 - Block user
POST   /v1/reports                - Report user
```

---

## 📋 Modules Overview

### Core Features
1. **Authentication** - Login, register, OAuth
2. **Discovery** - Profile browsing (cached deck)
3. **Interactions** - Likes, passes, super-likes
4. **Messaging** - Real-time chat over WebSocket + REST

### User Features
5. **Profile** - Account & dating profile management
6. **Subscriptions** - Premium tiers and in-app purchases
7. **Notifications** - Push notifications via Firebase

### Community
8. **Safety** - Blocks, reports, user suspension
9. **Verification** - Identity verification workflow
10. **Meetups** - Schedule meetups with matches

### Media
11. **Stories** - 24-hour expiring photos/text
12. **Reels** - Short-form videos

### Admin
13. **Admin Dashboard** - Analytics, moderation, reporting
14. **WebSocket** - Real-time event streaming

---

## 🔐 Authentication Methods

### JWT Bearer Token
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Obtain Token Via:
- `POST /v1/auth/register` - New account
- `POST /v1/auth/login` - Email/password
- `POST /v1/auth/oauth` - Google, Apple, Facebook

### Token Lifetime:
- Default: 10,080 minutes (7 days)
- Configurable via `UNKITTERED_JWT_TTL`

---

## 💾 Data Models

### User
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "John",
  "subscriptionTier": "free|premium|vip",
  "onboardingComplete": boolean,
  "createdAt": "ISO8601"
}
```

### Profile
```json
{
  "userId": "uuid",
  "name": "John",
  "age": 28,
  "bio": "string",
  "location": "San Francisco, CA",
  "interests": ["hiking", "tech"],
  "gallery": ["url1", "url2"],
  "isVerified": boolean,
  "isOnline": boolean
}
```

### Match
```json
{
  "id": "uuid",
  "profile": { ... },
  "createdAt": "ISO8601",
  "lastMessageAt": "ISO8601"
}
```

### Message
```json
{
  "id": "uuid",
  "isMe": boolean,
  "text": "string",
  "createdAt": "ISO8601",
  "readAt": "ISO8601 | null"
}
```

---

## 🛑 Error Handling

### Error Response Format
```json
{
  "timestamp": "2026-06-25T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid email format",
  "path": "/v1/auth/register"
}
```

### Status Codes
- `200` - OK
- `201` - Created
- `204` - No Content
- `400` - Bad Request (validation error)
- `401` - Unauthorized (invalid token)
- `403` - Forbidden (permission denied)
- `404` - Not Found
- `409` - Conflict (already exists)
- `429` - Too Many Requests (rate limited)
- `500` - Server Error

---

## ⚡ Rate Limiting

Per authenticated user:
- **Discover deck views:** 100/day
- **Likes:** 50/day (free), unlimited (premium)
- **Messages:** No hard limit
- **Photo uploads:** 10/day
- **General API calls:** 1,000/hour

---

## 🔌 Real-Time Chat (WebSocket)

### Connection
```
WS wss://api.unkittered.com/ws/chat?token=<JWT>
```

### Events
```json
{
  "type": "message",
  "data": {
    "id": "uuid",
    "matchId": "uuid",
    "senderId": "uuid",
    "text": "Hello!",
    "createdAt": "ISO8601"
  }
}
```

### Event Types
- `message` - New message
- `match` - New match created
- `typing` - User typing
- `presence` - Online/offline status
- `message_read` - Message read by recipient

---

## 📱 File Upload Limits

| Type | Max Size | Formats |
|------|----------|---------|
| **Photo** | 8 MB | JPEG, PNG, WebP |
| **Reel Video** | 100 MB | MP4, WebM |
| **Story Media** | 8 MB | JPEG, PNG, WebP |
| **ID Verification** | 8 MB | JPEG, PNG |

---

## 🗺️ Integrations

### External Services
- **Firebase** - Push notifications
- **AWS SQS** - Async event queue (LocalStack for local dev)
- **Redis** - Profile deck caching (60s TTL)
- **PostgreSQL** - Main database
- **Flyway** - Database migrations

### Feature Integrations
- **In-App Purchases** - Apple App Store, Google Play
- **OAuth** - Google, Apple, Facebook
- **Location Services** - Latitude/longitude for profiles
- **Device Tokens** - Firebase Cloud Messaging

---

## 📚 Using These Documents

### For Backend Integration
→ Use **ALL_ENDPOINTS.md** for complete reference with examples

### For Planning & Architecture
→ Use **MODULES.md** for module overview and integration checklist

### For Quick Lookup
→ Use **QUICK_REFERENCE.csv** for fast table lookups

### For Automation & Tools
→ Use **ENDPOINTS.json** for Postman, code generation, API tools

---

## 🔍 Finding an Endpoint

### By Feature
Open **MODULES.md** and find the module (e.g., "Safety")

### By HTTP Method
Open **QUICK_REFERENCE.csv** and filter by Method

### By Path Pattern
Open **ALL_ENDPOINTS.md** and search (Ctrl+F) for path

### Specific Example
Want to know how to send a message?
1. Open MODULES.md
2. Find "Messaging" module
3. Look for `POST /v1/conversations/{matchId}/messages`
4. Open ALL_ENDPOINTS.md for full request/response example

---

## 🚨 Common Issues

### "401 Unauthorized"
- Token missing or expired
- Solution: Get new token via `/v1/auth/login`

### "403 Forbidden"
- Admin endpoint, user lacks permission
- Solution: Check if endpoint is admin-only in MODULES.md

### "429 Too Many Requests"
- Rate limit exceeded
- Solution: Wait and retry (check rate limits in this README)

### "409 Conflict"
- Already liked this user
- Solution: Check if action already performed

---

## 📞 Support

For issues with:
- **API Endpoints** - Check ALL_ENDPOINTS.md
- **Architecture** - Check MODULES.md
- **Integration** - Check QUICK_REFERENCE.csv
- **Automation** - Use ENDPOINTS.json

---

## 📝 Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-06-25 | Initial endpoint documentation |

---

**Backend Version:** 0.1.0  
**API Version:** v1  
**Last Updated:** June 25, 2026
