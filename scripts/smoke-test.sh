#!/bin/bash
# End-to-end smoke test against a running API.
#   Slice 1: register, discover, like both ways → match.
#   V18:     structured pets, pledge + server-computed badge, typed chat
#            messages (plan → accept), and packs (pet policy, Gold waitlist).
# Emails are randomised so the script can be re-run against the same DB.
set -euo pipefail
BASE="${BASE:-http://localhost:8081/v1}"
RUN=$RANDOM$RANDOM
J() { python3 -c "import sys,json;print(json.load(sys.stdin)$1)"; }
expect() { [ "$1" == "$2" ] && echo "  ✓ $3" || { echo "  ✗ $3 (expected '$2', got '$1')"; exit 1; }; }
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# Bodies go through stdin so non-ASCII text stays UTF-8 on every OS
# (Windows shells re-encode command-line arguments).
send() { local method=$1 url=$2 body=$3; shift 3; curl -s -X "$method" "$url" "$@" --data-binary @- <<<"$body"; }

echo "▶ Register Alice and Bob"
A=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"alice$RUN@test.com\",\"password\":\"password123\",\"displayName\":\"Alice\"}")
ATOKEN=$(echo "$A" | J "['token']"); AID=$(echo "$A" | J "['user']['id']")
B=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"bob$RUN@test.com\",\"password\":\"password123\",\"displayName\":\"Bob\"}")
BTOKEN=$(echo "$B" | J "['token']"); BID=$(echo "$B" | J "['user']['id']")
AH=(-H "Authorization: Bearer $ATOKEN" -H 'Content-Type: application/json')
BH=(-H "Authorization: Bearer $BTOKEN" -H 'Content-Type: application/json')
# Decks only show members who finished onboarding.
curl -s -X POST "$BASE/me/onboarding/complete" "${AH[@]}" > /dev/null
curl -s -X POST "$BASE/me/onboarding/complete" "${BH[@]}" > /dev/null

echo "▶ Likes both ways → match"
curl -s -X POST "$BASE/likes" "${AH[@]}" -d "{\"profileId\":\"$BID\"}" > /dev/null
M=$(curl -s -X POST "$BASE/likes" "${BH[@]}" -d "{\"profileId\":\"$AID\"}" | J "['isMatch']")
expect "$M" "True" "mutual like is a match"
MATCH=$(curl -s "$BASE/matches" "${AH[@]}" | J "['matches'][0]['id']")
LEFT=$(curl -s "$BASE/subscriptions/me" "${AH[@]}" | J "['wagsLeftToday']")
expect "$(curl -s -X POST "$BASE/likes" "${AH[@]}" -d "{\"profileId\":\"$BID\"}" | J "['alreadyMatched']")" "True" "re-wagging a match says so"
expect "$(curl -s "$BASE/subscriptions/me" "${AH[@]}" | J "['wagsLeftToday']")" "$LEFT" "and costs no wag"

echo "▶ Pets + pledge (and isVerified is not client-settable)"
P=$(send PUT "$BASE/me/profile" '{
  "pledgePath":"sterilised","isVerified":true,
  "animals":[{"name":"Biscuit","species":"dog","breedAndAge":"Staffie mix · 4 yrs 🐕","energy":4,
              "getsAlongWith":["big_dogs","new_people"],"comesOnDates":true}]}' "${AH[@]}")
expect "$(echo "$P" | J "['animals'][0]['name']")" "Biscuit" "animals saved"
expect "$(echo "$P" | J "['animals'][0]['getsAlongWith']")" "['big_dogs', 'new_people']" "temperaments saved"
expect "$(echo "$P" | J "['pets'][0]")" "🐕 Biscuit" "legacy pets label derived"
expect "$(echo "$P" | J "['pledgePath']")" "sterilised" "pledge saved"
expect "$(echo "$P" | J "['pledgeRenewsAt'] is not None")" "True" "pledge renewal date set"
expect "$(echo "$P" | J "['badge']")" "none" "self-verification ignored"
expect "$(status -X PUT "$BASE/me/profile" "${AH[@]}" -d '{"animals":[{"name":"X","species":"dragon"}]}')" "400" "unknown species rejected"
expect "$(printf '{"name":"\xb7"}' | status -X PUT "$BASE/me/profile" "${AH[@]}" --data-binary @-)" "400" "invalid UTF-8 is a 400, not a 500"

echo "▶ Typed messages: pet note, plan, accept"
N=$(curl -s -X POST "$BASE/conversations/$MATCH/messages" "${AH[@]}" \
  -d '{"kind":"pet_note","payload":{"fromPet":"Biscuit","toPet":"Rex","text":"Walkies?"}}')
expect "$(echo "$N" | J "['kind']")" "pet_note" "pet note stored with kind"
expect "$(echo "$N" | J "['text']")" "Walkies?" "readable body kept"
STARTS=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=2)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
PLAN=$(send POST "$BASE/conversations/$MATCH/messages" "{\"kind\":\"plan\",\"payload\":{\"title\":\"Walk with Biscuit\",\"place\":\"Riverside Park\",\"startsAt\":\"$STARTS\",\"durationMinutes\":90,\"amenities\":[\"Café\"]}}" "${AH[@]}")
PLAN_ID=$(echo "$PLAN" | J "['payload']['id']")
expect "$(status -X POST "$BASE/conversations/$MATCH/messages" "${AH[@]}" \
  -d "{\"kind\":\"plan_response\",\"payload\":{\"planId\":\"$PLAN_ID\",\"accepted\":true}}")" "400" "can't accept own plan"
curl -s -X POST "$BASE/conversations/$MATCH/messages" "${BH[@]}" \
  -d "{\"kind\":\"plan_response\",\"payload\":{\"planId\":\"$PLAN_ID\",\"accepted\":true}}" > /dev/null
C=$(curl -s "$BASE/conversations" "${AH[@]}")
expect "$(echo "$C" | J "['conversations'][0]['planStatus']")" "accepted" "conversation shows accepted plan"
expect "$(echo "$C" | J "['conversations'][0]['plan']['place']")" "Riverside Park" "conversation carries the plan"
expect "$(status -X POST "$BASE/conversations/$MATCH/messages" "${AH[@]}" \
  -d '{"kind":"photo","payload":{"url":"https://evil.example/x.jpg"}}')" "400" "external photo URLs rejected"

echo "▶ Packs: pet policy, pets, full pack"
PK=$(curl -s -X POST "$BASE/meetups" "${AH[@]}" -d "{\"title\":\"Sunday Sniff\",\"kind\":\"walk\",\"petPolicy\":\"dogs_only\",
  \"area\":\"Riverside\",\"venue\":\"Riverside Park\",\"startsAt\":\"$STARTS\",\"capacity\":2,\"durationMinutes\":90}")
PACK=$(echo "$PK" | J "['id']")
expect "$(echo "$PK" | J "['kind']")" "walk" "pack kind stored"
expect "$(echo "$PK" | J "['category']")" "Walk" "legacy category derived"
J2=$(curl -s -X POST "$BASE/meetups/$PACK/rsvp" "${BH[@]}" -d '{"withPet":true}')
expect "$(echo "$J2" | J "['bringingPet']")" "True" "RSVP with pet"
expect "$(echo "$J2" | J "['petCount']")" "1" "pet counted"
C3=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"cleo$RUN@test.com\",\"password\":\"password123\",\"displayName\":\"Cleo\"}" | J "['token']")
expect "$(status -X POST "$BASE/meetups/$PACK/rsvp" -H "Authorization: Bearer $C3")" "409" "full pack rejects non-Gold"

echo "▶ Sterilisation badge + pause"
S=$(send PUT "$BASE/me/profile" '{"sterilisationProcedure":"vasectomy","interests":["Hiking buddy","Chess partner"]}' "${AH[@]}")
expect "$(echo "$S" | J "['sterilisationStatus']")" "declared" "procedure declared, unverified"
expect "$(echo "$S" | J "['sterilisationProcedure']")" "vasectomy" "procedure stored"
expect "$(echo "$S" | J "['interests']")" "['Hiking buddy', 'Chess partner']" "interests stored"
expect "$(status -X PUT "$BASE/me/profile" "${AH[@]}" -d '{"sterilisationProcedure":"magic"}')" "400" "unknown procedure rejected"
CL=$(send PUT "$BASE/me/profile" '{"sterilisationProcedure":""}' "${AH[@]}")
expect "$(echo "$CL" | J "['sterilisationProcedure']")" "None" "empty string clears the procedure"
expect "$(echo "$CL" | J "['sterilisationStatus']")" "declared" "sterilised pledge alone still declares"
expect "$(curl -s "$BASE/me/verification" "${AH[@]}" | J "['sterilisationStatus']")" "none" "proof review status: nothing submitted yet"
inDeck() { curl -s "$BASE/discover" -H "Authorization: Bearer $C3" | python3 -c "import json,sys;print(any(p['id']=='$1' for p in json.load(sys.stdin)['profiles']))"; }
expect "$(inDeck "$AID")" "True" "visible in decks before pausing"
C3ID=$(curl -s "$BASE/me" -H "Authorization: Bearer $C3" | J "['id']")
expect "$(curl -s "$BASE/discover" "${AH[@]}" | python3 -c "import json,sys;print(any(p['id']=='$C3ID' for p in json.load(sys.stdin)['profiles']))")" "False" "unfinished sign-ups stay out of decks"
expect "$(send PUT "$BASE/me/profile" '{"paused":true}' "${AH[@]}" | J "['paused']")" "True" "profile paused"
expect "$(inDeck "$AID")" "False" "paused profile hidden from decks"
send PUT "$BASE/me/profile" '{"paused":false}' "${AH[@]}" > /dev/null

echo "▶ Discovery filters"
CH=(-H "Authorization: Bearer $C3" -H 'Content-Type: application/json')
expect "$(curl -s "$BASE/me/discovery-prefs" "${CH[@]}" | J "['ageMin']")" "18" "defaults: age from 18"
expect "$(status -X PUT "$BASE/me/discovery-prefs" "${CH[@]}" -d '{"ageMin":12,"ageMax":40}')" "400" "under-18 range rejected"
PR=$(send PUT "$BASE/me/discovery-prefs" '{"ageMin":25,"ageMax":40,"sterilisedOnly":true,"species":["dog"],"interests":["Chess partner"]}' "${CH[@]}")
expect "$(echo "$PR" | J "['sterilisedOnly']")" "True" "prefs saved"
expect "$(echo "$PR" | J "['species']")" "['dog']" "species saved"
DECK=$(curl -s "$BASE/discover" -H "Authorization: Bearer $C3")
expect "$(echo "$DECK" | python3 -c "import json,sys;print(all(25<=p['age']<=40 and p['sterilisationStatus']!='none' for p in json.load(sys.stdin)['profiles']))")" "True" "deck honours the filters"

echo "▶ Delete account"
D=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"dee$RUN@test.com\",\"password\":\"password123\",\"displayName\":\"Dee\"}" | J "['token']")
DH=(-H "Authorization: Bearer $D" -H 'Content-Type: application/json')
expect "$(status -X DELETE "$BASE/me" "${DH[@]}" -d '{"password":"wrong-one"}')" "403" "wrong password is 403 (not a sign-out 401)"
expect "$(status -X DELETE "$BASE/me" "${DH[@]}" -d '{"password":"password123","reason":"smoke"}')" "204" "account deleted"
expect "$(status -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"dee$RUN@test.com\",\"password\":\"password123\"}")" "401" "deleted account can't sign in"

echo "▶ Secure password reset (needs UNKITTERED_LOG_RESET_CODES=true locally)"
LOG="${API_LOG:-/tmp/unkittered-api.log}"
expect "$(status -X POST "$BASE/auth/forgot-password" -H 'Content-Type: application/json' -d '{"email":"nobody'$RUN'@test.com"}')" "202" "unknown email gets the same 202 (no account probing)"
expect "$(status -X POST "$BASE/auth/reset-password" -H 'Content-Type: application/json' \
  -d "{\"email\":\"alice$RUN@test.com\",\"newPassword\":\"hijacked-pass\"}")" "400" "old email-only reset is refused"
expect "$(status -X POST "$BASE/auth/forgot-password" -H 'Content-Type: application/json' -d "{\"email\":\"alice$RUN@test.com\"}")" "202" "reset code requested"
sleep 1
CODE=$(grep "Password reset code for alice$RUN@test.com" "$LOG" | tail -1 | grep -oE '[0-9]{6}$')
expect "$(status -X POST "$BASE/auth/reset-password" -H 'Content-Type: application/json' \
  -d "{\"email\":\"alice$RUN@test.com\",\"code\":\"000000\",\"newPassword\":\"brand-new-pass\"}")" "400" "wrong code rejected"
expect "$(status -X POST "$BASE/auth/reset-password" -H 'Content-Type: application/json' \
  -d "{\"email\":\"alice$RUN@test.com\",\"code\":\"$CODE\",\"newPassword\":\"brand-new-pass\"}")" "200" "right code resets and signs in"
expect "$(status -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"alice$RUN@test.com\",\"password\":\"brand-new-pass\"}")" "200" "new password works"
expect "$(status -X POST "$BASE/auth/reset-password" -H 'Content-Type: application/json' \
  -d "{\"email\":\"alice$RUN@test.com\",\"code\":\"$CODE\",\"newPassword\":\"again-new-pass\"}")" "400" "a code works only once"

echo "▶ Google / Apple sign-in is off until configured (not an open door)"
expect "$(status -X POST "$BASE/auth/oauth" -H 'Content-Type: application/json' -d '{"provider":"google","idToken":"forged"}')" "503" "unconfigured provider answers 503"
expect "$(status -X POST "$BASE/auth/oauth" -H 'Content-Type: application/json' -d '{"provider":"google"}')" "400" "no ID token, no account"

echo "▶ Allowance and who-wagged privacy"
E=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"eve$RUN@test.com\",\"password\":\"password123\",\"displayName\":\"Eve\"}")
ETOKEN=$(echo "$E" | J "['token']"); EID=$(echo "$E" | J "['user']['id']")
EH=(-H "Authorization: Bearer $ETOKEN" -H 'Content-Type: application/json')
expect "$(curl -s "$BASE/subscriptions/me" "${EH[@]}" | J "['wagsLeftToday']")" "15" "free: 15 wags a day"
curl -s -X POST "$BASE/likes" "${EH[@]}" -d "{\"profileId\":\"$AID\"}" > /dev/null
expect "$(curl -s "$BASE/subscriptions/me" "${EH[@]}" | J "['wagsLeftToday']")" "14" "a wag spends one"
F=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"fay$RUN@test.com\",\"password\":\"password123\",\"displayName\":\"Fay\"}")
FT=$(echo "$F" | J "['token']"); FID=$(echo "$F" | J "['user']['id']")
FH=(-H "Authorization: Bearer $FT" -H 'Content-Type: application/json')
curl -s -X POST "$BASE/likes" "${FH[@]}" -d "{\"profileId\":\"$EID\"}" > /dev/null
W=$(curl -s "$BASE/likes/received" "${EH[@]}")
expect "$(echo "$W" | J "['profiles'][0]['id'].startswith('hidden-')")" "True" "free: who-wagged is an anonymous teaser"
expect "$(echo "$W" | J "['profiles'][0]['imageUrl']")" "None" "no photo leaves the server"
curl -s -X POST "$BASE/likes" "${EH[@]}" -d "{\"profileId\":\"$FID\"}" > /dev/null
expect "$(curl -s "$BASE/likes/received" "${EH[@]}" | J "['profiles'].__len__()")" "0" "wagging back (a match) clears them from who-wagged"

echo "▶ Paws wallet (needs UNKITTERED_MPESA_MODE=dev locally)"
CAT=$(curl -s "$BASE/wallet/catalog" "${AH[@]}")
expect "$(echo "$CAT" | J "['gifts'].__len__()")" "9" "nine gifts"
expect "$(echo "$CAT" | J "['mpesaEnabled']")" "True" "M-Pesa enabled (dev)"
expect "$(status -X POST "$BASE/me/wallet/top-ups" "${AH[@]}" -d '{"packId":"starter","phone":"12345"}')" "400" "bad phone rejected"
T=$(curl -s -X POST "$BASE/me/wallet/top-ups" "${AH[@]}" -d '{"packId":"starter","phone":"0712 345 678"}')
expect "$(echo "$T" | J "['wallet']['paws']")" "50" "top-up credited"
G=$(curl -s -X POST "$BASE/gifts" "${AH[@]}" -d "{\"toProfileId\":\"$BID\",\"giftId\":\"rose\"}")
expect "$(echo "$G" | J "['paws']")" "45" "rose costs 5"
expect "$(curl -s "$BASE/me/wallet" "${BH[@]}" | J "['earned']")" "2" "recipient keeps half (rounded down)"
expect "$(status -X POST "$BASE/gifts" "${AH[@]}" -d "{\"toProfileId\":\"$BID\",\"giftId\":\"diamond\"}")" "402" "can't afford a diamond"
expect "$(curl -s "$BASE/conversations/$MATCH/messages" "${BH[@]}" | J "['messages'][-1]['kind']")" "gift" "the gift shows up in the chat"
expect "$(status -X POST "$BASE/conversations/$MATCH/messages" "${AH[@]}" -d '{"kind":"gift","payload":{"giftId":"crown"}}')" "400" "nobody can fake a gift message"
expect "$(status -X POST "$BASE/me/wallet/withdrawals" "${BH[@]}" -d '{"paws":2,"phone":"0712345678"}')" "400" "withdrawals start at 500 earned"

echo "▶ Date Concierge"
DC=$(curl -s -X POST "$BASE/conversations/$MATCH/date-plans" "${AH[@]}")
expect "$(echo "$DC" | J "['plans'].__len__()")" "1" "free: one idea"
expect "$(echo "$DC" | J "['more'] > 0")" "True" "with a Gold teaser for the rest"

echo "▶ Location and the blurred map"
expect "$(status -X PUT "$BASE/me/location" "${AH[@]}" -d '{"latitude":-1.29207,"longitude":36.82195}')" "204" "location shared"
expect "$(status -X PUT "$BASE/me/location" "${AH[@]}" -d '{"latitude":123,"longitude":0}')" "400" "impossible coordinates rejected"
MAP=$(curl -s "$BASE/discover/map" "${AH[@]}")
expect "$(echo "$MAP" | J "['latitude'] != -1.29207")" "True" "your own spot comes back blurred too"

echo "▶ Reels and stories"
printf 'fake' > .smoke-reel.mp4; trap 'rm -f .smoke-reel.mp4' EXIT
expect "$(status -X POST "$BASE/me/reel" -H "Authorization: Bearer $ETOKEN" -F "file=@.smoke-reel.mp4;type=video/mp4")" "403" "free members can't post reels"
expect "$(status -X POST "$BASE/me/stories" -H "Authorization: Bearer $ETOKEN" -F "text=you are a bitch")" "400" "story text is moderated"
expect "$(status -X POST "$BASE/me/stories" -H "Authorization: Bearer $ETOKEN" -F "text=Sunday hike with Biscuit 🐾")" "200" "a kind story posts"

echo "▶ Get vouched for (single-use codes; verified members only)"
VC=$(curl -s -X POST "$BASE/me/vouch-invites" "${AH[@]}" | J "['code']")
expect "$(echo -n "$VC" | wc -c | tr -d ' ')" "8" "an 8-character code"
expect "$(curl -s -X POST "$BASE/me/vouch-invites" "${AH[@]}" | J "['code']")" "$VC" "asking again returns the same open code"
expect "$(status "$BASE/vouch-invites/$VC" "${AH[@]}")" "400" "you can't preview your own code"
expect "$(curl -s "$BASE/vouch-invites/$VC" "${BH[@]}" | J "['name']")" "Alice" "a friend sees whose code it is"
expect "$(status -X POST "$BASE/vouch-invites/$VC/accept" "${BH[@]}")" "403" "an unverified member can't vouch"
expect "$(status "$BASE/vouch-invites/NOPE2345" "${BH[@]}")" "404" "unknown codes are refused"
expect "$(curl -s "$BASE/me/verification" "${AH[@]}" | J "['vouchStatus']")" "none" "no vouch yet"

echo "✅ Smoke test complete."
