# API Testing Guide - Quick Commands

## Using with cURL

### 1. Register a New Account
```bash
curl -X POST http://localhost:8080/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePassword123",
    "displayName": "Test User"
  }'
```

### 2. Login and Get Token
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "SecurePassword123"
  }'

# Response will include: { "token": "eyJhbGc...", "user": {...} }
# Save the token for next requests
```

### 3. Get Profile Deck (Requires Auth)
```bash
TOKEN="eyJhbGc..."
curl -X GET http://localhost:8080/v1/discover \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Like a Profile
```bash
TOKEN="eyJhbGc..."
PROFILE_ID="uuid-here"

curl -X POST http://localhost:8080/v1/likes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"profileId\": \"$PROFILE_ID\"}"
```

### 5. Get Matches
```bash
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/v1/matches \
  -H "Authorization: Bearer $TOKEN"
```

### 6. Send a Message
```bash
TOKEN="eyJhbGc..."
MATCH_ID="uuid-here"

curl -X POST http://localhost:8080/v1/conversations/$MATCH_ID/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"text": "Hey! How are you?"}'
```

### 7. Update Profile
```bash
TOKEN="eyJhbGc..."

curl -X PUT http://localhost:8080/v1/me/profile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "age": 28,
    "bio": "Love hiking and coffee",
    "location": "San Francisco, CA",
    "interests": ["hiking", "coffee", "travel"]
  }'
```

### 8. Upload a Photo
```bash
TOKEN="eyJhbGc..."

curl -X POST http://localhost:8080/v1/me/photos \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/photo.jpg"
```

### 9. Block a User
```bash
TOKEN="eyJhbGc..."
PROFILE_ID="uuid-here"

curl -X POST http://localhost:8080/v1/blocks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"profileId\": \"$PROFILE_ID\"}"
```

### 10. Report a User
```bash
TOKEN="eyJhbGc..."
PROFILE_ID="uuid-here"

curl -X POST http://localhost:8080/v1/reports \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "profileId": "'$PROFILE_ID'",
    "reason": "inappropriate_content",
    "details": "Posted explicit photos"
  }'
```

### 11. Get Notification Preferences
```bash
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/v1/me/notification-prefs \
  -H "Authorization: Bearer $TOKEN"
```

### 12. Update Notification Preferences
```bash
TOKEN="eyJhbGc..."

curl -X PUT http://localhost:8080/v1/me/notification-prefs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "matches": true,
    "messages": true,
    "reminders": false,
    "likes": true
  }'
```

### 13. Register Device for Push Notifications
```bash
TOKEN="eyJhbGc..."

curl -X POST http://localhost:8080/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "token": "firebase-device-token-string",
    "platform": "ios"
  }'
```

### 14. Get Current Subscription
```bash
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/v1/subscriptions/me \
  -H "Authorization: Bearer $TOKEN"
```

### 15. Create a Meetup
```bash
TOKEN="eyJhbGc..."

curl -X POST http://localhost:8080/v1/meetups \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Coffee at Blue Bottle",
    "description": "Lets meet up for coffee!",
    "location": "San Francisco, CA",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "scheduledAt": "2026-06-28T14:00:00Z"
  }'
```

### 16. Admin: Get Stats
```bash
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/v1/admin/stats \
  -H "Authorization: Bearer $TOKEN"
```

### 17. Admin: Get Pending Verifications
```bash
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/v1/admin/verifications \
  -H "Authorization: Bearer $TOKEN"
```

### 18. Admin: Approve Verification
```bash
TOKEN="eyJhbGc..."
USER_ID="uuid-here"

curl -X POST http://localhost:8080/v1/admin/verifications/$USER_ID/approve \
  -H "Authorization: Bearer $TOKEN"
```

### 19. Admin: Suspend User
```bash
TOKEN="eyJhbGc..."
USER_ID="uuid-here"

curl -X POST http://localhost:8080/v1/admin/users/$USER_ID/suspend \
  -H "Authorization: Bearer $TOKEN"
```

### 20. Check Admin Status
```bash
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/v1/admin/me \
  -H "Authorization: Bearer $TOKEN"

# Response: { "isAdmin": true/false }
```

---

## Using with Postman

### 1. Import Collection
1. Open Postman
2. Click "Import"
3. Paste the JSON from ENDPOINTS.json or use URL: `http://localhost:8080/swagger-ui.html`

### 2. Set Base URL
1. Create environment variable: `{{baseUrl}}` = `http://localhost:8080/v1`
2. Add to all requests: `{{baseUrl}}/endpoint`

### 3. Set Authorization
1. In Postman, go to "Authorization" tab
2. Select "Bearer Token"
3. Paste token in the "Token" field
4. Check "Prepend 'Bearer' to the token"

### 4. Automate Token Refresh
In the `/v1/auth/login` request:
1. Go to "Tests" tab
2. Add script:
```javascript
if (pm.response.code === 200) {
    var response = pm.response.json();
    pm.globals.set("token", response.token);
    pm.globals.set("userId", response.user.id);
}
```

---

## Using with Python

### Quick Setup
```python
import requests

BASE_URL = "http://localhost:8080/v1"

# 1. Register
register_response = requests.post(
    f"{BASE_URL}/auth/register",
    json={
        "email": "test@example.com",
        "password": "SecurePassword123",
        "displayName": "Test User"
    }
)
token = register_response.json()["token"]

# 2. Get profile deck
headers = {"Authorization": f"Bearer {token}"}
discover_response = requests.get(
    f"{BASE_URL}/discover",
    headers=headers
)
profiles = discover_response.json()["profiles"]
print(f"Found {len(profiles)} profiles")

# 3. Like a profile
profile_id = profiles[0]["id"]
like_response = requests.post(
    f"{BASE_URL}/likes",
    headers=headers,
    json={"profileId": profile_id}
)
print(f"Is match: {like_response.json()['isMatch']}")

# 4. Get matches
matches_response = requests.get(
    f"{BASE_URL}/matches",
    headers=headers
)
matches = matches_response.json()["matches"]
print(f"You have {len(matches)} matches!")

# 5. Send message
if matches:
    match_id = matches[0]["id"]
    message_response = requests.post(
        f"{BASE_URL}/conversations/{match_id}/messages",
        headers=headers,
        json={"text": "Hey! How are you?"}
    )
    print(f"Message sent: {message_response.json()['text']}")
```

---

## Using with JavaScript/Node.js

### Quick Setup
```javascript
const BASE_URL = "http://localhost:8080/v1";

async function testAPI() {
  // 1. Register
  const registerRes = await fetch(`${BASE_URL}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: "test@example.com",
      password: "SecurePassword123",
      displayName: "Test User"
    })
  });
  
  const { token } = await registerRes.json();
  const headers = { 
    "Authorization": `Bearer ${token}`,
    "Content-Type": "application/json"
  };
  
  // 2. Get profile deck
  const discoverRes = await fetch(`${BASE_URL}/discover`, { headers });
  const { profiles } = await discoverRes.json();
  console.log(`Found ${profiles.length} profiles`);
  
  // 3. Like a profile
  const likeRes = await fetch(`${BASE_URL}/likes`, {
    method: "POST",
    headers,
    body: JSON.stringify({ profileId: profiles[0].id })
  });
  
  const { isMatch } = await likeRes.json();
  console.log(`Is match: ${isMatch}`);
  
  // 4. Get matches
  const matchesRes = await fetch(`${BASE_URL}/matches`, { headers });
  const { matches } = await matchesRes.json();
  console.log(`You have ${matches.length} matches!`);
  
  // 5. Send message
  if (matches.length > 0) {
    const messageRes = await fetch(
      `${BASE_URL}/conversations/${matches[0].id}/messages`,
      {
        method: "POST",
        headers,
        body: JSON.stringify({ text: "Hey! How are you?" })
      }
    );
    
    const message = await messageRes.json();
    console.log(`Message sent: ${message.text}`);
  }
}

testAPI();
```

---

## WebSocket Connection (Node.js)

```javascript
const WebSocket = require('ws');

const TOKEN = "eyJhbGc..."; // Get from login
const WS_URL = `ws://localhost:8080/ws/chat?token=${TOKEN}`;

const ws = new WebSocket(WS_URL);

ws.on('open', () => {
  console.log('Connected to WebSocket');
});

ws.on('message', (data) => {
  const event = JSON.parse(data);
  
  switch (event.type) {
    case 'message':
      console.log(`New message: ${event.data.text}`);
      break;
    case 'match':
      console.log(`New match with ${event.data.profile.name}!`);
      break;
    case 'typing':
      console.log('User is typing...');
      break;
    case 'presence':
      console.log(`User ${event.data.userId} is ${event.data.status}`);
      break;
  }
});

ws.on('error', (error) => {
  console.error('WebSocket error:', error);
});

ws.on('close', () => {
  console.log('Disconnected from WebSocket');
});
```

---

## Health Check

### API Health
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

---

## Swagger UI

Interactive API documentation available at:
```
http://localhost:8080/swagger-ui.html
```

---

**Last Updated:** June 25, 2026
