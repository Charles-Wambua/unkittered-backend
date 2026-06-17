#!/bin/bash
# End-to-end smoke test for the 5 slice-1 endpoints.
# Registers two users, browses, likes both ways, and confirms a match + who-liked-you.
set -e
BASE="${BASE:-http://localhost:8080/v1}"
J() { python3 -c "import sys,json;print(json.load(sys.stdin)$1)"; }

echo "▶ Register Alice"
A=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d '{"email":"alice@test.com","password":"password123","displayName":"Alice"}')
ATOKEN=$(echo "$A" | J "['token']"); AID=$(echo "$A" | J "['user']['id']")
echo "  Alice id=$AID"

echo "▶ Register Bob"
B=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d '{"email":"bob@test.com","password":"password123","displayName":"Bob"}')
BTOKEN=$(echo "$B" | J "['token']"); BID=$(echo "$B" | J "['user']['id']")
echo "  Bob id=$BID"

echo "▶ Login Alice"
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"alice@test.com","password":"password123"}' | J "['user']['displayName']"

echo "▶ Alice discover deck (count)"
curl -s "$BASE/discover" -H "Authorization: Bearer $ATOKEN" | J "['profiles'].__len__()"

echo "▶ Alice likes Bob (expect isMatch=false)"
curl -s -X POST "$BASE/likes" -H "Authorization: Bearer $ATOKEN" \
  -H 'Content-Type: application/json' -d "{\"profileId\":\"$BID\"}" | J "['isMatch']"

echo "▶ Bob's who-liked-you (expect Alice)"
curl -s "$BASE/likes/received" -H "Authorization: Bearer $BTOKEN" | J "['profiles'][0]['name']"

echo "▶ Bob likes Alice back (expect isMatch=true)"
curl -s -X POST "$BASE/likes" -H "Authorization: Bearer $BTOKEN" \
  -H 'Content-Type: application/json' -d "{\"profileId\":\"$AID\"}" | J "['isMatch']"

echo "✅ Smoke test complete — check the app log for the '🎉 It's a match!' SQS line."
