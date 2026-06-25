# Unkittered API - Endpoints by Module

## Overview
This document organizes all 54 API endpoints by their functional module for easy navigation and integration.

---

## 📊 Endpoint Count by Module

| Module | Count | Status |
|--------|-------|--------|
| Authentication | 5 | ✅ Complete |
| Discovery | 1 | ✅ Complete |
| Interactions | 4 | ✅ Complete |
| Messaging | 5 | ✅ Complete |
| Profile | 5 | ✅ Complete |
| Safety | 4 | ✅ Complete |
| Subscriptions | 4 | ✅ Complete |
| Notifications | 4 | ✅ Complete |
| Stories | 3 | ✅ Complete |
| Reels | 3 | ✅ Complete |
| Meetups | 6 | ✅ Complete |
| Verification | 2 | ✅ Complete |
| Admin | 12 | ✅ Complete |
| WebSocket | 1 | ✅ Complete |
| **TOTAL** | **59** | ✅ |

---

## Module Details

### 🔐 Authentication (5 endpoints)
No authentication required for these endpoints.

```
POST   /v1/auth/register          → Create new account
POST   /v1/auth/login             → Login with email/password
POST   /v1/auth/oauth             → OAuth login (Google/Apple/Facebook)
POST   /v1/auth/reset-password    → Reset forgotten password
POST   /v1/auth/logout            → Logout (return 204)
```

**Key Response:** All auth endpoints return `{ token: jwt, user: {...} }`

---

### 🎯 Discovery (1 endpoint)
Browse and discover potential matches.

```
GET    /v1/discover               → Get 30 profiles (Redis cached)
```

**Cache Details:**
- TTL: 60 seconds
- Size: 30 profiles per request
- Rate limit: 100 views/day (free users)

---

### 💕 Interactions (4 endpoints)
Core dating interactions: liking, passing, matching.

```
POST   /v1/likes                  → Like a profile → { isMatch, matchId }
POST   /v1/super-likes            → Super like (premium) → { isMatch, matchId }
POST   /v1/passes                 → Pass on profile (return 204)
GET    /v1/likes/received         → Get received likes → { profiles: [...] }
```

**Limits:**
- Free: 50 likes/day
- Premium: Unlimited likes
- Super-likes: Limited based on subscription

---

### 💬 Messaging (5 endpoints)
Match management and real-time chat.

```
GET    /v1/matches                → Get all matches → { matches: [...] }
GET    /v1/conversations          → Get conversations → { conversations: [...] }
GET    /v1/conversations/{matchId}/messages  → Get messages (marks read)
POST   /v1/conversations/{matchId}/messages  → Send message
DELETE /v1/matches/{matchId}      → Unmatch (return 204)
```

**Real-time:** Messages also delivered via WebSocket at `/ws/chat`

---

### 👤 Profile (5 endpoints)
User account and profile management.

```
GET    /v1/me                     → Get current user
GET    /v1/me/profile             → Get dating profile
PUT    /v1/me/profile             → Update profile (all fields optional)
POST   /v1/me/onboarding/complete → Complete onboarding
POST   /v1/me/photos              → Upload photo (multipart, 8MB max)
```

**Profile Fields:**
- name, age, bio, location (latitude/longitude)
- interests, gallery, pets, occupation, education
- showActivity, hideDistance, incognitoMode, connectionMode

---

### 🛡️ Safety (4 endpoints)
User safety, moderation, and reporting.

```
POST   /v1/blocks                 → Block user (return 204)
DELETE /v1/blocks/{profileId}     → Unblock user (return 204)
GET    /v1/blocks                 → Get blocked users list
POST   /v1/reports                → Report user for violation (return 204)
```

**Report Reasons:**
- `inappropriate_content`
- `harassment`
- `fake_profile`
- `other`

---

### 💳 Subscriptions (4 endpoints)
Subscription tiers and premium features.

```
GET    /v1/subscriptions/me       → Get current subscription tier
POST   /v1/subscriptions/boost    → Apply boost (premium, 24h visibility)
POST   /v1/subscriptions/verify   → Verify in-app purchase
POST   /v1/subscriptions/cancel   → Cancel subscription (return 204)
```

**Tiers:** `free`, `premium`, `vip`

**Features by Tier:**
- Free: Basic discover, likes, messaging
- Premium: Unlimited likes, super-likes, boost
- VIP: Premium + priority support

---

### 🔔 Notifications (4 endpoints)
Push notification device management and preferences.

```
POST   /v1/devices                → Register device token (return 204)
DELETE /v1/devices/{token}        → Unregister device (return 204)
GET    /v1/me/notification-prefs  → Get notification settings
PUT    /v1/me/notification-prefs  → Update notification settings
```

**Preference Toggles:**
- matches, messages, reminders, likes, superLikes, boosts

---

### 📖 Stories (3 endpoints)
24-hour expiring user stories.

```
GET    /v1/stories                → Get story feed
POST   /v1/me/stories             → Create story (multipart: file + text)
DELETE /v1/me/stories             → Delete all stories (return 204)
```

**Details:**
- Expires after 24 hours
- Can be image or text or both
- Max 8MB file size

---

### 🎬 Reels (3 endpoints)
Short-form video content.

```
GET    /v1/reels                  → Get reel feed
POST   /v1/me/reel                → Upload reel (multipart: video + optional poster)
DELETE /v1/me/reel                → Delete reel (return 204)
```

**Details:**
- Max 100MB video
- Supports MP4, WebM
- Optional poster image for preview

---

### 📍 Meetups (6 endpoints)
Schedule and manage meetups with matches.

```
GET    /v1/meetups                → Get all meetups
POST   /v1/meetups                → Create new meetup
POST   /v1/meetups/{id}/rsvp      → RSVP to meetup
DELETE /v1/meetups/{id}/rsvp      → Cancel RSVP
DELETE /v1/meetups/{id}           → Delete meetup (return 204)
POST   /v1/meetups/{id}/report    → Report meetup (return 204)
```

**Meetup Fields:**
- title, description, location (lat/long)
- scheduledAt (ISO8601)
- attendees array

---

### ✅ Verification (2 endpoints)
Identity verification for verified badge.

```
GET    /v1/me/verification        → Get verification status
POST   /v1/me/verification        → Submit ID photo (multipart)
```

**Status Values:**
- `not_started` → No submission yet
- `pending` → Awaiting admin review
- `approved` → Verified badge enabled
- `rejected` → Resubmit required

**Admin Review:** Within 24 hours

---

### ⚙️ Admin (12 endpoints)
Admin dashboard and moderation tools (admin-only).

```
GET    /v1/admin/stats            → User/match/message statistics
GET    /v1/admin/me               → Check if current user is admin
GET    /v1/admin/verifications    → List pending verifications
POST   /v1/admin/verifications/{userId}/approve  → Approve verification
POST   /v1/admin/verifications/{userId}/reject   → Reject verification
GET    /v1/admin/reports          → List all reports
DELETE /v1/admin/reports/{type}/{id} → Dismiss report
POST   /v1/admin/users/{id}/suspend   → Suspend user (return 204)
POST   /v1/admin/users/{id}/unsuspend → Unsuspend user (return 204)
GET    /v1/admin/insights         → Analytics insights
GET    /v1/admin/live             → Real-time statistics
GET    /v1/admin/endpoints        → List all endpoints (meta)
GET    /v1/admin/system           → System health status
```

**Admin Features:**
- User suspension/unsuspension
- Verification approval workflow
- Report management
- Real-time dashboard metrics
- System monitoring

---

### 🌐 WebSocket (1 endpoint)
Real-time bidirectional communication for chat.

```
WS     /ws/chat?token=<JWT>       → Upgrade to WebSocket (101)
```

**Events Received:**
- `message` → New message in conversation
- `match` → New match created
- `typing` → User typing indicator
- `presence` → User online/offline status
- `message_read` → Message read by recipient

**Connection:**
1. Client connects with valid JWT
2. Server authenticates and associates connection
3. Client receives real-time event stream
4. Send/receive messages in real-time

---

## Integration Checklist

### Phase 1: Core Auth & Discovery
- [ ] POST /v1/auth/register
- [ ] POST /v1/auth/login
- [ ] GET /v1/discover

### Phase 2: Interactions & Matching
- [ ] POST /v1/likes
- [ ] POST /v1/passes
- [ ] GET /v1/matches

### Phase 3: Messaging
- [ ] GET /v1/conversations
- [ ] GET /v1/conversations/{matchId}/messages
- [ ] POST /v1/conversations/{matchId}/messages
- [ ] WS /ws/chat

### Phase 4: Profile Management
- [ ] GET /v1/me/profile
- [ ] PUT /v1/me/profile
- [ ] POST /v1/me/photos

### Phase 5: Safety
- [ ] POST /v1/blocks
- [ ] GET /v1/blocks
- [ ] POST /v1/reports

### Phase 6: Premium Features
- [ ] POST /v1/subscriptions/verify
- [ ] POST /v1/super-likes
- [ ] POST /v1/subscriptions/boost

### Phase 7: Advanced Features
- [ ] POST /v1/me/stories
- [ ] GET /v1/reels
- [ ] POST /v1/meetups

---

## Error Response Format

All endpoints follow this error format:

```json
{
  "timestamp": "2026-06-25T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "path": "/v1/likes"
}
```

**Common Status Codes:**
- `400` - Bad Request (validation error)
- `401` - Unauthorized (missing/invalid token)
- `403` - Forbidden (admin-only or insufficient permissions)
- `404` - Not Found (resource doesn't exist)
- `409` - Conflict (already liked/matched)
- `429` - Too Many Requests (rate limited)
- `500` - Server Error

---

## Rate Limiting

Per authenticated user:
- Discover: 100 requests/day
- Likes: 50/day (free), unlimited (premium)
- Messages: No hard limit
- API calls: 1000/hour

---

## Documentation Files in This Folder

1. **ALL_ENDPOINTS.md** - Detailed endpoint reference with examples
2. **QUICK_REFERENCE.csv** - CSV table of all endpoints
3. **ENDPOINTS.json** - Structured JSON API specification
4. **MODULES.md** - This file (endpoints organized by module)
5. **POSTMAN_COLLECTION.json** - Postman collection (if provided)

---

**Last Updated:** June 25, 2026  
**API Version:** v1  
**Backend Version:** 0.1.0
