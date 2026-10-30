// Unkittered public site: theme toggle, mobile menu, scroll reveal,
// contents highlighting and copy buttons. Everything degrades without JS.
(function () {
  var root = document.documentElement;
  root.classList.remove('no-js');

  function stored() {
    try { return localStorage.getItem('uk-theme'); } catch (e) { return null; }
  }
  function store(v) {
    try { localStorage.setItem('uk-theme', v); } catch (e) { /* private mode */ }
  }
  var saved = stored();
  if (saved === 'light' || saved === 'dark') root.setAttribute('data-theme', saved);

  function isDark() {
    var t = root.getAttribute('data-theme');
    if (t) return t === 'dark';
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  document.addEventListener('DOMContentLoaded', function () {
    var themeBtn = document.querySelector('.theme-btn');
    if (themeBtn) {
      var label = function () {
        themeBtn.setAttribute('aria-label', isDark() ? 'Switch to light mode' : 'Switch to dark mode');
      };
      label();
      themeBtn.addEventListener('click', function () {
        var next = isDark() ? 'light' : 'dark';
        root.setAttribute('data-theme', next);
        store(next);
        label();
      });
    }

    var nav = document.querySelector('.nav');
    var menuBtn = document.querySelector('.menu-btn');
    if (nav && menuBtn) {
      menuBtn.addEventListener('click', function () {
        var open = nav.classList.toggle('open');
        menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
      });
      nav.querySelectorAll('.nav-links a').forEach(function (a) {
        a.addEventListener('click', function () { nav.classList.remove('open'); });
      });
    }

    var reveals = document.querySelectorAll('.reveal');
    if ('IntersectionObserver' in window) {
      var ro = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) {
          if (e.isIntersecting) { e.target.classList.add('in'); ro.unobserve(e.target); }
        });
      }, { rootMargin: '0px 0px -8% 0px' });
      reveals.forEach(function (el) { ro.observe(el); });
    } else {
      reveals.forEach(function (el) { el.classList.add('in'); });
    }

    var tocLinks = document.querySelectorAll('.toc a[href^="#"]');
    if (tocLinks.length && 'IntersectionObserver' in window) {
      var byId = {};
      tocLinks.forEach(function (a) { byId[a.getAttribute('href').slice(1)] = a; });
      var to = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) {
          if (!e.isIntersecting) return;
          tocLinks.forEach(function (a) { a.classList.remove('active'); });
          var a = byId[e.target.id];
          if (a) a.classList.add('active');
        });
      }, { rootMargin: '-20% 0px -70% 0px' });
      Object.keys(byId).forEach(function (id) {
        var s = document.getElementById(id);
        if (s) to.observe(s);
      });
    }

    document.querySelectorAll('[data-copy]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var text = btn.getAttribute('data-copy');
        var done = function () {
          var old = btn.textContent;
          btn.textContent = 'Copied';
          setTimeout(function () { btn.textContent = old; }, 1600);
        };
        if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, function () {});
      });
    });

    var year = document.querySelector('[data-year]');
    if (year) year.textContent = String(new Date().getFullYear());
  });
})();
