# Unkittered API - Complete Endpoints Reference

**Base URL:** `http://localhost:8080/v1` (local) | Production URL (set at deployment)

**Authentication:** Bearer token in `Authorization` header for all secured endpoints
```
Authorization: Bearer <JWT_TOKEN>
```

---

## Table of Contents
1. [Authentication](#1-authentication)
2. [Discovery](#2-discovery)
3. [Interactions (Likes/Passes)](#3-interactions-likespasses)
4. [Messaging & Chat](#4-messaging--chat)
5. [Profile & Account](#5-profile--account)
6. [Safety & Moderation](#6-safety--moderation)
7. [Subscriptions](#7-subscriptions)
8. [Notifications & Devices](#8-notifications--devices)
9. [Stories](#9-stories)
10. [Reels](#10-reels)
11. [Meetups](#11-meetups)
12. [Verification](#12-verification)
13. [Admin](#13-admin)
14. [WebSocket (Real-time)](#14-websocket-real-time)

---

## 1. AUTHENTICATION

### Public Endpoints (No Auth Required)

#### Register
```
POST /v1/auth/register
Status: 201 Created

Request:
{
  "email": "user@example.com",
  "password": "securePassword123",
  "displayName": "John Doe"
}

Response:
{
  "token": "eyJhbGc...",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "John Doe",
    "subscriptionTier": "free",
    "onboardingComplete": false
  }
}
```

#### Login
```
POST /v1/auth/login

Request:
{
  "email": "user@example.com",
  "password": "securePassword123"
}

Response:
{
  "token": "eyJhbGc...",
  "user": { ... }
}
```

#### OAuth Login
```
POST /v1/auth/oauth

Request:
{
  "provider": "google|apple|facebook",
  "token": "provider-oauth-token"
}

Response:
{
  "token": "eyJhbGc...",
  "user": { ... }
}
```

#### Password Reset
```
POST /v1/auth/reset-password

Request:
{
  "email": "user@example.com",
  "newPassword": "newSecurePassword123"
}

Response:
{
  "token": "eyJhbGc...",
  "user": { ... }
}
```

#### Logout
```
POST /v1/auth/logout
Status: 204 No Content

Request: (empty body)
Response: (empty)
```

---

## 2. DISCOVERY

### Get Profile Deck (Cached)
```
GET /v1/discover
Auth: Required

Response:
{
  "profiles": [
    {
      "id": "uuid",
      "name": "Alice",
      "age": 25,
      "imageUrl": "https://...",
      "bio": "Love hiking and coffee",
      "location": "San Francisco, CA",
      "interests": ["hiking", "coffee", "travel"],
      "gallery": ["url1", "url2", "url3"],
      "isVerified": true,
      "occupation": "Product Manager",
      "education": "UC Berkeley",
      "isOnline": true,
      "lastActiveAt": "2026-06-25T10:30:00Z"
    },
    ...
  ]
}

Cache TTL: 60 seconds
Deck Size: 30 profiles
```

---

## 3. INTERACTIONS (Likes/Passes)

### Like Profile
```
POST /v1/likes
Auth: Required

Request:
{
  "profileId": "target-uuid"
}

Response:
{
  "isMatch": true|false,
  "matchId": "match-uuid-or-null",
  "createdAt": "2026-06-25T10:30:00Z"
}

Notes:
- If mutual like → isMatch=true, matchId returned
- Publishes async event to SQS for push notification
```

### Super Like Profile
```
POST /v1/super-likes
Auth: Required

Request:
{
  "profileId": "target-uuid"
}

Response:
{
  "isMatch": true|false,
  "matchId": "match-uuid-or-null"
}

Notes:
- Premium feature (subscription tier check)
- Triggers special notification to target user
```

### Pass on Profile
```
POST /v1/passes
Auth: Required
Status: 204 No Content

Request:
{
  "profileId": "target-uuid"
}

Response: (empty)

Notes:
- Prevents profile from reappearing in deck for 90 days
```

### Get Received Likes
```
GET /v1/likes/received
Auth: Required

Response:
{
  "profiles": [
    {
      "id": "uuid",
      "name": "Bob",
      "age": 28,
      "imageUrl": "https://...",
      ...
    },
    ...
  ]
}

Notes:
- Shows profiles that have liked current user
- Sorted by most recent
```

---

## 4. MESSAGING & CHAT

### Get All Matches
```
GET /v1/matches
Auth: Required

Response:
{
  "matches": [
    {
      "id": "match-uuid",
      "profile": {
        "id": "uuid",
        "name": "Alice",
        "imageUrl": "https://...",
        ...
      },
      "createdAt": "2026-06-20T14:30:00Z",
      "lastMessageAt": "2026-06-25T10:15:00Z"
    },
    ...
  ]
}
```

### Get All Conversations
```
GET /v1/conversations
Auth: Required

Response:
{
  "conversations": [
    {
      "matchId": "match-uuid",
      "profile": {
        "id": "uuid",
        "name": "Alice",
        "imageUrl": "https://...",
        "isOnline": true
      },
      "lastMessage": "Hey! How are you?",
      "unreadCount": 2,
      "lastMessageAt": "2026-06-25T10:15:00Z"
    },
    ...
  ]
}
```

### Get Messages (Marks as Read)
```
GET /v1/conversations/{matchId}/messages
Auth: Required

Response:
{
  "messages": [
    {
      "id": "msg-uuid",
      "isMe": false,
      "text": "Hey! How are you?",
      "createdAt": "2026-06-25T10:15:00Z",
      "readAt": "2026-06-25T10:16:00Z"
    },
    {
      "id": "msg-uuid",
      "isMe": true,
      "text": "I'm doing great!",
      "createdAt": "2026-06-25T10:17:00Z",
      "readAt": "2026-06-25T10:17:30Z"
    },
    ...
  ]
}

Notes:
- Auto-marks all messages in conversation as read
- Paginated (check for limit/offset params if needed)
```

### Send Message
```
POST /v1/conversations/{matchId}/messages
Auth: Required

Request:
{
  "text": "Hey! How are you?"
}

Response:
{
  "id": "msg-uuid",
  "isMe": true,
  "text": "Hey! How are you?",
  "createdAt": "2026-06-25T10:18:00Z",
  "readAt": null
}

Notes:
- Message moderation applied (profanity/safety checks)
- Async event published for real-time push
- Delivery over WebSocket if recipient online
```

### Unmatch
```
DELETE /v1/matches/{matchId}
Auth: Required
Status: 204 No Content

Response: (empty)

Notes:
- Removes conversation and match history
- Blocks further communication
```

---

## 5. PROFILE & ACCOUNT

### Get Current User
```
GET /v1/me
Auth: Required

Response:
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "John Doe",
  "subscriptionTier": "premium",
  "onboardingComplete": true,
  "createdAt": "2026-01-15T08:00:00Z",
  "updatedAt": "2026-06-25T10:30:00Z"
}
```

### Get User Profile
```
GET /v1/me/profile
Auth: Required

Response:
{
  "userId": "uuid",
  "name": "John Doe",
  "age": 28,
  "imageUrl": "https://...",
  "bio": "Software engineer, love outdoor activities",
  "location": "San Francisco, CA",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "interests": ["hiking", "tech", "travel"],
  "gallery": ["url1", "url2", "url3"],
  "pets": ["dog", "cat"],
  "isVerified": true,
  "occupation": "Software Engineer",
  "education": "MIT",
  "childFreeStatement": "Open to children",
  "cardQuote": "Life is an adventure",
  "isOnline": true,
  "lastActiveAt": "2026-06-25T10:30:00Z",
  "showActivity": true,
  "hideDistance": false,
  "incognitoMode": false,
  "connectionMode": "both",
  "createdAt": "2026-01-15T08:00:00Z",
  "updatedAt": "2026-06-25T10:30:00Z"
}
```

### Update Profile
```
PUT /v1/me/profile
Auth: Required

Request (all fields optional):
{
  "name": "John Doe",
  "age": 28,
  "bio": "Love hiking and tech",
  "location": "San Francisco, CA",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "interests": ["hiking", "tech", "travel"],
  "pets": ["dog"],
  "occupation": "Software Engineer",
  "education": "MIT",
  "childFreeStatement": "Open to children",
  "cardQuote": "Life is an adventure",
  "showActivity": true,
  "hideDistance": false,
  "incognitoMode": false,
  "connectionMode": "both"
}

Response: (updated profile object)
```

### Complete Onboarding
```
POST /v1/me/onboarding/complete
Auth: Required

Request: (empty body)

Response:
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "John Doe",
  "subscriptionTier": "free",
  "onboardingComplete": true,
  "createdAt": "2026-01-15T08:00:00Z",
  "updatedAt": "2026-06-25T10:30:00Z"
}
```

### Upload Photo
```
POST /v1/me/photos
Auth: Required
Content-Type: multipart/form-data

Request:
{
  "file": <binary-image-data>
}

Response:
{
  "url": "https://your-server/files/photo-uuid.jpg"
}

Notes:
- Max file size: 8MB
- Supported: JPEG, PNG, WebP
- Stored locally or on S3 (configurable)
- URL returned for immediate use
```

---

## 6. SAFETY & MODERATION

### Block User
```
POST /v1/blocks
Auth: Required
Status: 204 No Content

Request:
{
  "profileId": "target-uuid"
}

Response: (empty)

Notes:
- If match exists, unmatch automatically
- Blocked user cannot see current user's profile
- Blocks are one-way (target can still see you unless they block back)
```

### Unblock User
```
DELETE /v1/blocks/{profileId}
Auth: Required
Status: 204 No Content

Response: (empty)
```

### Get Blocked Users
```
GET /v1/blocks
Auth: Required

Response:
{
  "profiles": [
    {
      "id": "uuid",
      "name": "Bad User",
      "imageUrl": "https://...",
      "blockedAt": "2026-06-20T14:30:00Z"
    },
    ...
  ]
}
```

### Report User
```
POST /v1/reports
Auth: Required
Status: 204 No Content

Request:
{
  "profileId": "target-uuid",
  "reason": "inappropriate_content|harassment|fake_profile|other",
  "details": "Optional additional context"
}

Response: (empty)

Notes:
- Reviewed by admin team
- Multiple reports trigger user suspension
- Reporter anonymity maintained
```

---

## 7. SUBSCRIPTIONS

### Get Current Subscription
```
GET /v1/subscriptions/me
Auth: Required

Response:
{
  "tier": "free|premium|vip",
  "renewalDate": "2026-07-25T00:00:00Z",
  "isCanceled": false,
  "cancellationDate": null,
  "features": {
    "superLikes": 10,
    "profiles": true,
    "messaging": true,
    "boost": false
  }
}
```

### Apply Boost (Premium Feature)
```
POST /v1/subscriptions/boost
Auth: Required

Response:
{
  "expiresAt": "2026-06-26T10:30:00Z",
  "boostCount": 1,
  "remainingBoosts": 0
}

Notes:
- Requires premium subscription
- Makes profile visible to more users for 24 hours
- One boost per month on premium
```

### Verify In-App Purchase
```
POST /v1/subscriptions/verify
Auth: Required

Request:
{
  "productId": "ios|android product ID",
  "receipt": "receipt-data-from-app-store"
}

Response:
{
  "tier": "premium|vip",
  "renewalDate": "2026-07-25T00:00:00Z",
  "isActive": true
}

Notes:
- Validates with Apple App Store or Google Play
- Auto-upgrades user tier
```

### Cancel Subscription
```
POST /v1/subscriptions/cancel
Auth: Required
Status: 204 No Content

Request: (empty body)

Response: (empty)

Notes:
- Cancels renewal (access continues until renewal date)
- Can be reactivated by new purchase
```

---

## 8. NOTIFICATIONS & DEVICES

### Register Device Token
```
POST /v1/devices
Auth: Required
Status: 204 No Content

Request:
{
  "token": "firebase-device-token-string",
  "platform": "ios|android"
}

Response: (empty)

Notes:
- Stores device for push notification delivery
- Multiple tokens per user supported
```

### Unregister Device Token
```
DELETE /v1/devices/{token}
Auth: Required
Status: 204 No Content

Request: (empty body)

Response: (empty)

Notes:
- Removes device from push notification roster
```

### Get Notification Preferences
```
GET /v1/me/notification-prefs
Auth: Required

Response:
{
  "matches": true,
  "messages": true,
  "reminders": true,
  "likes": true,
  "superLikes": true,
  "boosts": false
}
```

### Update Notification Preferences
```
PUT /v1/me/notification-prefs
Auth: Required

Request:
{
  "matches": true,
  "messages": true,
  "reminders": true,
  "likes": false,
  "superLikes": false,
  "boosts": false
}

Response: (updated prefs)
```

---

## 9. STORIES

### Get Story Feed
```
GET /v1/stories
Auth: Required

Response:
{
  "stories": [
    {
      "id": "story-uuid",
      "userId": "uuid",
      "profile": {
        "name": "Alice",
        "imageUrl": "https://..."
      },
      "mediaUrl": "https://...",
      "text": "Optional caption",
      "views": 42,
      "createdAt": "2026-06-25T10:30:00Z",
      "expiresAt": "2026-06-26T10:30:00Z"
    },
    ...
  ]
}

Notes:
- Stories expire after 24 hours
- Sorted by creation date
```

### Create Story
```
POST /v1/me/stories
Auth: Required
Content-Type: multipart/form-data

Request:
{
  "file": <optional-binary-image/video>,
  "text": "Optional caption"
}

Response:
{
  "id": "story-uuid",
  "mediaUrl": "https://...",
  "createdAt": "2026-06-25T10:30:00Z",
  "expiresAt": "2026-06-26T10:30:00Z"
}

Notes:
- Either file or text required (or both)
- Max 8MB file size
- Expires 24 hours after creation
```

### Delete Story
```
DELETE /v1/me/stories
Auth: Required

Response: (empty)

Notes:
- Deletes all current stories from user
```

---

## 10. REELS

### Get Reel Feed
```
GET /v1/reels
Auth: Required

Response:
{
  "reels": [
    {
      "id": "reel-uuid",
      "userId": "uuid",
      "profile": {
        "name": "Bob",
        "imageUrl": "https://..."
      },
      "videoUrl": "https://...",
      "posterUrl": "https://...",
      "likes": 120,
      "comments": 8,
      "shares": 5,
      "createdAt": "2026-06-25T10:30:00Z"
    },
    ...
  ]
}

Notes:
- Short-form video content
- Sorted by creation date
```

### Upload Reel
```
POST /v1/me/reel
Auth: Required
Content-Type: multipart/form-data

Request:
{
  "file": <binary-video-data>,
  "poster": <optional-binary-poster-image>
}

Response:
{
  "id": "reel-uuid",
  "videoUrl": "https://...",
  "posterUrl": "https://...",
  "createdAt": "2026-06-25T10:30:00Z"
}

Notes:
- Max video size: 100MB
- Supported: MP4, WebM
- Poster is optional preview image
```

### Delete Reel
```
DELETE /v1/me/reel
Auth: Required

Response: (empty)

Notes:
- Deletes current reel
```

---

## 11. MEETUPS

### Get Meetups
```
GET /v1/meetups
Auth: Required

Response:
{
  "meetups": [
    {
      "id": "meetup-uuid",
      "creatorId": "uuid",
      "title": "Coffee @ Blue Bottle",
      "description": "Let's meet up!",
      "location": "San Francisco, CA",
      "latitude": 37.7749,
      "longitude": -122.4194,
      "scheduledAt": "2026-06-28T14:00:00Z",
      "attendees": [
        {
          "userId": "uuid",
          "name": "Alice",
          "imageUrl": "https://..."
        },
        ...
      ],
      "attendeeCount": 5,
      "rsvpStatus": "joined|not-joined",
      "createdAt": "2026-06-25T10:30:00Z"
    },
    ...
  ]
}
```

### Create Meetup
```
POST /v1/meetups
Auth: Required

Request:
{
  "title": "Coffee @ Blue Bottle",
  "description": "Let's meet up!",
  "location": "San Francisco, CA",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "scheduledAt": "2026-06-28T14:00:00Z"
}

Response: (created meetup object)
```

### RSVP to Meetup
```
POST /v1/meetups/{id}/rsvp
Auth: Required

Response:
{
  "id": "meetup-uuid",
  "attendeeCount": 6,
  "rsvpStatus": "joined",
  ...
}
```

### Cancel RSVP
```
DELETE /v1/meetups/{id}/rsvp
Auth: Required

Response:
{
  "id": "meetup-uuid",
  "attendeeCount": 5,
  "rsvpStatus": "not-joined",
  ...
}
```

### Delete Meetup
```
DELETE /v1/meetups/{id}
Auth: Required

Response: (empty)

Notes:
- Creator only
```

### Report Meetup
```
POST /v1/meetups/{id}/report
Auth: Required

Request (optional):
{
  "reason": "unsafe_location|harassment|other",
  "details": "Optional details"
}

Response: (empty)
```

---

## 12. VERIFICATION

### Get Verification Status
```
GET /v1/me/verification
Auth: Required

Response:
{
  "status": "not_started|pending|approved|rejected",
  "submittedAt": "2026-06-25T10:30:00Z",
  "approvedAt": "2026-06-26T14:00:00Z",
  "rejectionReason": null
}
```

### Submit Verification (ID Photo)
```
POST /v1/me/verification
Auth: Required
Content-Type: multipart/form-data

Request:
{
  "file": <binary-id-photo-data>
}

Response:
{
  "status": "pending",
  "submittedAt": "2026-06-25T10:30:00Z"
}

Notes:
- Admin reviews within 24 hours
- Must show clear face and ID
- Enables verified badge on profile
```

---

## 13. ADMIN

### Get Admin Stats
```
GET /v1/admin/stats
Auth: Required (Admin only)

Response:
{
  "totalUsers": 5420,
  "newUsersToday": 45,
  "activeUsers24h": 1200,
  "totalMatches": 3200,
  "totalMessages": 18500,
  "totalReports": 42
}
```

### Check Admin Status
```
GET /v1/admin/me
Auth: Required

Response:
{
  "isAdmin": true|false
}
```

### Get Pending Verifications
```
GET /v1/admin/verifications
Auth: Required (Admin only)

Response:
{
  "verifications": [
    {
      "userId": "uuid",
      "userName": "Alice",
      "imageUrl": "https://...",
      "submittedAt": "2026-06-25T10:30:00Z"
    },
    ...
  ]
}
```

### Approve Verification
```
POST /v1/admin/verifications/{userId}/approve
Auth: Required (Admin only)
Status: 204 No Content

Response: (empty)

Notes:
- User receives notification
- Verified badge added to profile
```

### Reject Verification
```
POST /v1/admin/verifications/{userId}/reject
Auth: Required (Admin only)
Status: 204 No Content

Response: (empty)

Notes:
- User notified of rejection
```

### Get All Reports
```
GET /v1/admin/reports
Auth: Required (Admin only)

Response:
{
  "reports": [
    {
      "id": "report-uuid",
      "reportedUserId": "uuid",
      "reportedUserName": "Bad Actor",
      "reporterName": "Anonymous",
      "reason": "inappropriate_content",
      "details": "Posted explicit photos",
      "status": "open|resolved|dismissed",
      "createdAt": "2026-06-25T10:30:00Z"
    },
    ...
  ]
}
```

### Dismiss Report
```
DELETE /v1/admin/reports/{type}/{id}
Auth: Required (Admin only)
Status: 204 No Content

Response: (empty)
```

### Suspend User
```
POST /v1/admin/users/{id}/suspend
Auth: Required (Admin only)
Status: 204 No Content

Response: (empty)

Notes:
- User account locked
- Cannot login or interact
```

### Unsuspend User
```
POST /v1/admin/users/{id}/unsuspend
Auth: Required (Admin only)
Status: 204 No Content

Response: (empty)
```

### Get Admin Insights
```
GET /v1/admin/insights
Auth: Required (Admin only)

Response:
{
  "insights": {
    "dailyActiveUsers": 1200,
    "weeklyRetention": 0.65,
    "averageSessionDuration": 15,
    "topFeatures": ["discover", "chat", "likes"],
    "trends": { ... }
  }
}
```

### Get Live Stats
```
GET /v1/admin/live
Auth: Required (Admin only)

Response:
{
  "onlineUsers": 340,
  "activeConversations": 120,
  "messagesPerSecond": 4.2,
  "errors": 2
}

Notes:
- Real-time dashboard data
```

### List All Endpoints (Meta)
```
GET /v1/admin/endpoints
Auth: Required (Admin only)

Response:
{
  "endpoints": [
    {
      "method": "GET",
      "path": "/v1/discover",
      "auth": true,
      "description": "Get profile deck"
    },
    ...
  ]
}

Notes:
- API documentation endpoint
```

### Get System Status
```
GET /v1/admin/system
Auth: Required (Admin only)

Response:
{
  "status": "healthy|degraded|down",
  "uptime": 98.5,
  "database": "connected",
  "redis": "connected",
  "sqs": "connected",
  "firebase": "connected",
  "version": "0.1.0"
}
```

---

## 14. WEBSOCKET (Real-time)

### Connect to Chat WebSocket
```
WS /ws/chat?token=<JWT_TOKEN>
```

**Connection Flow:**
1. Client connects with valid JWT token
2. Server authenticates and associates connection to user
3. Real-time events streamed to client

**Events Received:**
- `message` - New message in conversation
- `message_read` - Message read by recipient
- `typing` - User is typing
- `match` - New match created
- `presence` - User online/offline status

**Example Event (Message):**
```json
{
  "type": "message",
  "data": {
    "id": "msg-uuid",
    "matchId": "match-uuid",
    "senderId": "uuid",
    "text": "Hey there!",
    "createdAt": "2026-06-25T10:30:00Z"
  }
}
```

**Example Event (Match):**
```json
{
  "type": "match",
  "data": {
    "matchId": "match-uuid",
    "profile": {
      "id": "uuid",
      "name": "Alice",
      "imageUrl": "https://..."
    },
    "createdAt": "2026-06-25T10:30:00Z"
  }
}
```

---

## Error Responses

All endpoints return standard error format:

```
HTTP 400 Bad Request
{
  "timestamp": "2026-06-25T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "path": "/v1/likes"
}
```

**Common Status Codes:**
- `200 OK` - Successful GET/PUT
- `201 Created` - Successful POST (register, create)
- `204 No Content` - Successful DELETE/action
- `400 Bad Request` - Invalid input
- `401 Unauthorized` - Missing/invalid token
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `409 Conflict` - Already liked/matched
- `429 Too Many Requests` - Rate limited
- `500 Internal Server Error` - Server error

---

## Authentication Header Format

All authenticated endpoints require:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Token is obtained from:
- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/oauth`

---

## Rate Limiting

Applied per user:
- **Discover**: 100 deck views per day
- **Likes**: 50 likes per day (free), unlimited (premium)
- **Messages**: No hard limit, per-conversation moderation
- **Uploads**: 10 per day
- **API calls**: 1000 per hour

---

## Base URL Configuration

**Local Development:**
```
http://localhost:8080/v1
```

**Production:**
```
https://api.unkittered.com/v1
```

Set via:
```
UNKITTERED_PUBLIC_BASE_URL=https://api.unkittered.com
```

---

**Last Updated:** June 25, 2026  
**API Version:** v1  
**Backend Version:** 0.1.0
