"""Creates test members with photos, pets, matches, chats, a story, a reel and a pack.

  set UNKITTERED_TEST_PASSWORD=...           (one password for every test account)
  py -3 scripts/seed-test-users.py accounts https://HOST/v1 IMAGES_DIR
  # on the VM:  sudo -u postgres psql unkittered -f scripts/seed-test-users.sql
  py -3 scripts/seed-test-users.py activity https://HOST/v1 IMAGES_DIR

"accounts" registers everyone, fills in profiles and pets and submits the
verification selfies. The SQL step approves those selfies and gives Daniel and
Alex paid tiers (reels need Plus or Gold). "activity" then creates the matches,
chats, wags, a story, a reel and a pack meetup. Both steps are safe to re-run.

IMAGES_DIR holds the photos named below (portraits p5/p9/p13/p45/p47, dog1,
dog2, cat1, reel.mp4). They are not in the repo. Every account uses the
@unkittered.test domain, which can never receive mail.
"""
import json
import os
import subprocess
import sys
from datetime import datetime, timedelta, timezone

DOMAIN = 'unkittered.test'

USERS = {
    'alex': dict(
        name='Alex', age=30, photos=['p45.jpg'],
        profile=dict(
            bio='Your test account. Weekend runner, plant hoarder, and Luna\'s personal chef.',
            location='Lavington, Nairobi', occupation='Software engineer',
            childFreeStatement='Child-free by choice', pledgePath='child_free_by_choice',
            connectionMode='both', interests=['Running club', 'Coffee dates', 'Travel buddy'],
            cardQuote='Luna has final say on all dates.',
            animals=[dict(name='Luna', species='dog', breedAndAge='Sheltie · 4 yrs', energy=3,
                          getsAlongWith=['small_dogs', 'big_dogs', 'new_people'], comesOnDates=True)]),
        pet_photo='dog2.jpg', lat=-1.2803, lng=36.7724),
    'daniel': dict(
        name='Daniel', age=31, photos=['p13.jpg'],
        profile=dict(
            bio='Weekend hiker, weekday coffee snob. Biscuit and I are looking for trail friends.',
            location='Westlands, Nairobi', occupation='Product designer',
            childFreeStatement='Intentionally child-free', pledgePath='sterilised',
            sterilisationProcedure='vasectomy', connectionMode='both',
            interests=['Hiking buddy', 'Coffee dates', 'Board games'],
            cardQuote='Sunday = long walk, longer brunch.',
            animals=[dict(name='Biscuit', species='dog', breedAndAge='Westie · 3 yrs', energy=4,
                          getsAlongWith=['big_dogs', 'new_people'], comesOnDates=True)]),
        pet_photo='dog1.jpg', lat=-1.2676, lng=36.8108),
    'maya': dict(
        name='Maya', age=28, photos=['p47.jpg'],
        profile=dict(
            bio='Cat mum, bookshop regular, terrible at karaoke but I go anyway.',
            location='Kilimani, Nairobi', occupation='Vet nurse',
            childFreeStatement='Child-free by choice', pledgePath='child_free_by_choice',
            connectionMode='both', interests=['Book club', 'Karaoke', 'Travel buddy'],
            cardQuote='Looking for someone my cat approves of.',
            animals=[dict(name='Pixel', species='cat', breedAndAge='Tabby · 5 yrs', energy=2,
                          getsAlongWith=['cats', 'new_people'], comesOnDates=False)]),
        pet_photo='cat1.jpg', lat=-1.2921, lng=36.7856),
    'amara': dict(
        name='Amara', age=27, photos=['p5.jpg'],
        profile=dict(
            bio='Snow-day person living in the sunshine. Fostering dogs until one refuses to leave.',
            location='Kileleshwa, Nairobi', occupation='Architect',
            childFreeStatement='Child-free by choice', pledgePath='child_free_by_choice',
            connectionMode='both', interests=['Dog fostering', 'Museums', 'Coffee dates'],
            cardQuote='Ask me about my foster fails.', animals=[]),
        lat=-1.2741, lng=36.7840),
    'zoe': dict(
        name='Zoe', age=29, photos=['p9.jpg'],
        profile=dict(
            bio='Photographer. I host the Saturday Karura pack walk, come say hi.',
            location='Gigiri, Nairobi', occupation='Photographer',
            childFreeStatement='Intentionally child-free', pledgePath='child_free_by_choice',
            connectionMode='both', interests=['Photography', 'Pack walks', 'Hiking buddy'],
            cardQuote='Golden hour, every hour.', animals=[]),
        lat=-1.2334, lng=36.8030),
}
# Registered only, so the onboarding flow can be tested from the start.
FRESH = 'newuser'


def email(key):
    return f'{key}@{DOMAIN}'


class Api:
    def __init__(self, base, images, password):
        self.base, self.images, self.password = base.rstrip('/'), images, password
        self.tokens = {}

    def _curl(self, *args, body=None):
        out = subprocess.run(['curl', '-s', '--ssl-no-revoke', *args],
                             input=None if body is None else json.dumps(body).encode(),
                             capture_output=True).stdout.decode('utf-8', 'replace')
        try:
            return json.loads(out) if out.strip() else None
        except json.JSONDecodeError:
            return out

    def call(self, key, method, path, body=None):
        h = ['-H', 'Content-Type: application/json']
        if key:
            h += ['-H', f'Authorization: Bearer {self.token(key)}']
        extra = ['--data-binary', '@-'] if body is not None else []
        return self._curl('-X', method, self.base + path, *h, *extra, body=body)

    def upload(self, key, path, file, mime='image/jpeg', **fields):
        args = ['-X', 'POST', self.base + path, '-H', f'Authorization: Bearer {self.token(key)}',
                '-F', f'file=@{os.path.join(self.images, file)};type={mime}']
        for k, v in fields.items():
            args += ['-F', f'{k}={v}']
        return self._curl(*args)

    def token(self, key):
        if key not in self.tokens:
            r = self.call(None, 'POST', '/auth/login', {'email': email(key), 'password': self.password})
            if not isinstance(r, dict) or 'token' not in r:
                raise SystemExit(f'login failed for {email(key)}: {r}')
            self.tokens[key] = r['token']
        return self.tokens[key]

    def register(self, key, name):
        r = self.call(None, 'POST', '/auth/register',
                      {'email': email(key), 'password': self.password, 'displayName': name})
        if isinstance(r, dict) and 'token' in r:
            self.tokens[key] = r['token']
            return 'created'
        self.token(key)  # already registered: make sure the password still works
        return 'exists'

    def user_id(self, key):
        return self.call(key, 'GET', '/me')['id']


def accounts(api):
    for key, u in USERS.items():
        state = api.register(key, u['name'])
        urls = [api.upload(key, '/me/photos', f)['url'] for f in u['photos']]
        prof = dict(u['profile'], name=u['name'], age=u['age'], imageUrl=urls[0], galleryImages=urls)
        if prof['animals']:
            prof['animals'][0]['photoUrl'] = api.upload(key, '/me/photos', u['pet_photo'])['url']
        p = api.call(key, 'PUT', '/me/profile', prof)
        api.call(key, 'PUT', '/me/location', {'latitude': u['lat'], 'longitude': u['lng']})
        api.call(key, 'POST', '/me/onboarding/complete', {})
        v = api.upload(key, '/me/verification', u['photos'][0])
        ok = isinstance(p, dict) and p.get('name') == u['name']
        print(f"{email(key):24} {state:8} profile {'ok' if ok else p}  selfie {v}")
    print(f'{email(FRESH):24} {api.register(FRESH, "New")}')


def activity(api):
    ids = {k: api.user_id(k) for k in USERS}

    # Mutual wags make matches; one-way wags show up in Alex's "Who wagged".
    for a, b in [('daniel', 'alex'), ('alex', 'daniel'), ('maya', 'alex'), ('alex', 'maya'),
                 ('amara', 'alex'), ('zoe', 'alex'), ('daniel', 'maya'), ('maya', 'daniel')]:
        r = api.call(a, 'POST', '/likes', {'profileId': ids[b]})
        print(f'{a} wags {b}:', 'match' if r.get('isMatch') else 'wag' if isinstance(r, dict) else r)

    lines = {
        'daniel': ['Hey Alex! Biscuit saw Luna and has opinions. Karura on Sunday?',
                   'We usually start at the Limuru Road gate around 10.'],
        'maya': ['Hi 👋 Pixel says hello to Luna (from a safe distance).',
                 'What are you reading at the moment?'],
    }
    for key, texts in lines.items():
        match = next((m for m in api.call(key, 'GET', '/matches')['matches']
                      if ids['alex'] in json.dumps(m)), None)
        if not match:
            print(key, 'has no match with alex yet')
            continue
        existing = api.call(key, 'GET', f"/conversations/{match['id']}/messages").get('messages', [])
        if existing:
            print(key, 'chat already has', len(existing), 'messages')
            continue
        for t in texts:
            api.call(key, 'POST', f"/conversations/{match['id']}/messages", {'kind': 'text', 'text': t})
        print(key, 'sent', len(texts), 'messages to alex')

    print('maya story:', api.upload('maya', '/me/stories', 'cat1.jpg',
                                    text='Pixel supervising my Saturday reading 📚🐾'))
    print('daniel story:', api.upload('daniel', '/me/stories', 'dog1.jpg',
                                      text='Biscuit after the long loop 🥾'))
    print('daniel reel:', api.upload('daniel', '/me/reel', 'reel.mp4', mime='video/mp4'))

    meetups = api.call('zoe', 'GET', '/meetups').get('meetups', [])
    walk = next((m for m in meetups if m.get('title') == 'Saturday Karura pack walk'), None)
    if not walk:
        saturday = datetime.now(timezone.utc) + timedelta(days=(5 - datetime.now().weekday()) % 7 or 7)
        walk = api.call('zoe', 'POST', '/meetups', dict(
            title='Saturday Karura pack walk', area='Gigiri', venue='Karura Forest, Limuru Rd gate',
            startsAt=saturday.replace(hour=6, minute=0, second=0, microsecond=0).isoformat(),
            capacity=12, durationMinutes=120, kind='walk', petPolicy='pets_welcome',
            description='Easy 5 km loop with dogs on leads. Coffee at the River Café after.'))
        print('zoe hosts:', walk.get('title', walk) if isinstance(walk, dict) else walk)
    for key in ('daniel', 'maya'):
        r = api.call(key, 'POST', f"/meetups/{walk['id']}/rsvp", {'withPet': True})
        print(key, 'rsvp:', r.get('attendeeCount', r) if isinstance(r, dict) else r)


if __name__ == '__main__':
    if len(sys.argv) != 4 or sys.argv[1] not in ('accounts', 'activity'):
        raise SystemExit(__doc__)
    password = os.environ.get('UNKITTERED_TEST_PASSWORD')
    if not password or len(password) < 8:
        raise SystemExit('Set UNKITTERED_TEST_PASSWORD (8+ characters).')
    api = Api(sys.argv[2], sys.argv[3], password)
    {'accounts': accounts, 'activity': activity}[sys.argv[1]](api)
