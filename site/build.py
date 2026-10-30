"""Builds the public Unkittered site into src/main/resources/static/.

Each page in site/pages/ is a body fragment; this wraps it in the shared head,
nav and footer, and fills {{placeholders}} from CONFIG. Run after editing:

    py -3 site/build.py

Shared CSS, JS, fonts and images live in src/main/resources/static/site/.
"""
import html
import pathlib
import re
import sys

CONFIG = {
    # Public origin the pages are served from (nginx -> Spring Boot static).
    'base': 'https://unkittered.directpublish.duckdns.org',
    # Where complaints, privacy requests and deletion requests go.
    'email': 'unkittered@gmail.com',
    'updated': '31 October 2026',
    'updated_iso': '2026-10-31',
}

# (source fragment, output path under static/, <title>, meta description, nav key)
PAGES = [
    ('home.html', 'index.html', 'Unkittered: dating and friends for the child-free',
     'Dating, friends and meetups for people who are child-free by choice, and the animals who come with them.', 'home'),
    ('privacy.html', 'privacy/index.html', 'Privacy Policy · Unkittered',
     'What Unkittered collects, why, who it is shared with, and your rights.', 'privacy'),
    ('terms.html', 'terms/index.html', 'Terms of Service · Unkittered',
     'The rules for using Unkittered, including membership, Paws and payments.', 'terms'),
    ('guidelines.html', 'guidelines/index.html', 'Community Guidelines · Unkittered',
     'How we expect members to treat each other on Unkittered.', 'guidelines'),
    ('safety.html', 'safety/index.html', 'Safety · Unkittered',
     'Tips for meeting people safely, and how to report or block someone.', 'safety'),
    ('delete-account.html', 'delete-account/index.html', 'Delete your account · Unkittered',
     'How to delete your Unkittered account and data, in the app or by email.', 'delete'),
    ('support.html', 'support/index.html', 'Support · Unkittered',
     'Get help, report a member, or make a privacy request.', 'support'),
    ('404.html', 'error/404.html', 'Not found · Unkittered', 'This page does not exist.', ''),
]

PAW = ('<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 11.5c-2.6 0-5.6 3.6-5.6 6.1 0 1.6 1.2 2.4 2.6 2.4 '
       '1.2 0 2-.6 3-.6s1.8.6 3 .6c1.4 0 2.6-.8 2.6-2.4 0-2.5-3-6.1-5.6-6.1z"/><ellipse cx="5.3" cy="10" rx="2" ry="2.6"/>'
       '<ellipse cx="9.2" cy="5.8" rx="2" ry="2.7"/><ellipse cx="14.8" cy="5.8" rx="2" ry="2.7"/>'
       '<ellipse cx="18.7" cy="10" rx="2" ry="2.6"/></svg>')

NAV = [('safety', '/safety', 'Safety'), ('guidelines', '/guidelines', 'Guidelines'),
       ('privacy', '/privacy', 'Privacy'), ('support', '/support', 'Support')]

SHELL = """<!doctype html>
<html lang="en" class="no-js">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{title}</title>
<meta name="description" content="{description}">
<meta name="theme-color" content="#F5F0E8" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#17120F" media="(prefers-color-scheme: dark)">
<link rel="canonical" href="{base}{path}">
<meta property="og:title" content="{title}">
<meta property="og:description" content="{description}">
<meta property="og:type" content="website">
<meta property="og:url" content="{base}{path}">
<meta property="og:image" content="{base}/site/img/icon-512.png">
<link rel="icon" type="image/png" href="/site/img/favicon.png">
<link rel="apple-touch-icon" href="/site/img/apple-touch-icon.png">
<link rel="preload" href="/site/fonts/CormorantGaramond-Bold.ttf" as="font" type="font/ttf" crossorigin>
<link rel="preload" href="/site/fonts/DMSans-Regular.ttf" as="font" type="font/ttf" crossorigin>
<link rel="stylesheet" href="/site/site.css?v={version}">
<script>try{{var t=localStorage.getItem('uk-theme');if(t==='light'||t==='dark')document.documentElement.setAttribute('data-theme',t)}}catch(e){{}}</script>
<script src="/site/site.js?v={version}" defer></script>
</head>
<body>
<div class="backdrop" aria-hidden="true"><span></span><span></span><span></span></div>
<a class="sr-skip" href="#main">Skip to content</a>
<header class="nav">
  <div class="nav-inner">
    <a class="brand" href="/" aria-label="Unkittered home">
      <span class="brand-mark">{paw}</span><span class="brand-word">Un<i>kittered</i></span>
    </a>
    <ul class="nav-links" id="nav-links">{nav}</ul>
    <button class="icon-btn theme-btn" type="button" aria-label="Switch theme">
      <svg class="sun" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
      <svg class="moon" viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/></svg>
    </button>
    <button class="icon-btn menu-btn" type="button" aria-label="Menu" aria-expanded="false" aria-controls="nav-links">
      <svg viewBox="0 0 24 24"><path d="M4 8h16M4 16h16"/></svg>
    </button>
  </div>
</header>
<main id="main">
{body}
</main>
<footer class="footer">
  <div class="wrap">
    <div class="footer-grid">
      <div>
        <a class="brand" href="/"><span class="brand-mark">{paw}</span><span class="brand-word">Un<i>kittered</i></span></a>
        <p class="tag">Dating, friends and meetups for the child-free, and the animals who come with them.</p>
      </div>
      <div>
        <h4>Community</h4>
        <ul><li><a href="/safety">Safety</a></li><li><a href="/guidelines">Guidelines</a></li><li><a href="/support">Report a member</a></li></ul>
      </div>
      <div>
        <h4>Legal</h4>
        <ul><li><a href="/privacy">Privacy</a></li><li><a href="/terms">Terms</a></li><li><a href="/delete-account">Delete account</a></li></ul>
      </div>
      <div>
        <h4>Contact</h4>
        <ul><li><a href="/support">Help centre</a></li><li><a href="mailto:{email}">{email}</a></li></ul>
      </div>
    </div>
    <div class="footer-bottom">
      <span>&copy; <span data-year>2026</span> Unkittered. Adults 18+ only.</span>
      <span>Made for people, their cats, dogs and everyone in between.</span>
    </div>
  </div>
</footer>
</body>
</html>
"""

HERE = pathlib.Path(__file__).resolve().parent
STATIC = HERE.parent / 'src' / 'main' / 'resources' / 'static'


def nav_html(active):
    items = []
    for key, href, label in NAV:
        cur = ' aria-current="page"' if key == active else ''
        items.append(f'<li><a href="{href}"{cur}>{label}</a></li>')
    return ''.join(items)


def fill(text):
    def sub(m):
        key = m.group(1)
        if key not in CONFIG:
            sys.exit(f'Unknown placeholder {{{{{key}}}}}')
        return CONFIG[key]
    return re.sub(r'\{\{(\w+)\}\}', sub, text)


def main():
    if CONFIG['email'].endswith('@example.com'):
        print('WARNING: CONFIG["email"] is still a placeholder; set the real support address before deploying.')
    version = CONFIG['updated_iso'].replace('-', '')
    for src, out, title, desc, nav in PAGES:
        body = fill((HERE / 'pages' / src).read_text(encoding='utf-8'))
        path = '/' if out == 'index.html' else '/' + out.removesuffix('index.html').rstrip('/')
        page = SHELL.format(title=html.escape(title), description=html.escape(desc), base=CONFIG['base'],
                            path=path, version=version, paw=PAW, nav=nav_html(nav), body=body,
                            email=CONFIG['email'])
        dest = STATIC / out
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(page, encoding='utf-8', newline='\n')
        print(f'  {out}')


if __name__ == '__main__':
    main()
