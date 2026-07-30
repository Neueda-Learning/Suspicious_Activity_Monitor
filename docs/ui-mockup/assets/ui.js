/* ==========================================================================
   Shared UI helpers for the mockup.
   Formatting helpers mirror js/api.js so they can be lifted across verbatim.
   ========================================================================== */

/* ---------- escaping / formatting (same contracts as js/api.js) ---------- */
function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function bandLabel(band) {
  return band === 'GREEN' ? 'Low Priority' : band;
}

function fmtMoney(v) {
  if (v == null) return '—';
  return new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(Number(v));
}

function fmtTs(v) {
  if (!v) return '—';
  return new Date(v).toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC';
}

/** Short absolute stamp for dense tables: "28 Jul 16:52". */
function fmtShort(v) {
  if (!v) return '—';
  return new Date(v).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'UTC'
  });
}

/**
 * Relative SLA phrasing derived from slaDueAt, which the queue already returns.
 * Purely a re-presentation of an existing field — no new data is requested.
 */
function fmtRelative(v, now = Date.now()) {
  if (!v) return { text: '—', tone: '' };
  const diff = new Date(v).getTime() - now;
  const abs = Math.abs(diff);
  const h = Math.round(abs / 3600000);
  const d = Math.round(abs / 86400000);
  const unit = abs < 86400000 ? `${h}h` : `${d}d`;
  if (diff < 0) return { text: `${unit} overdue`, tone: 'over' };
  if (abs < 86400000) return { text: `in ${unit}`, tone: 'soon' };
  return { text: `in ${unit}`, tone: '' };
}

/** ISO-3166 alpha-2 → regional-indicator flag. Decorative only. */
function flag(cc) {
  if (!cc || cc.length !== 2) return '';
  return String.fromCodePoint(...[...cc.toUpperCase()].map(c => 0x1f1a5 + c.charCodeAt(0)));
}

function initials(name) {
  if (!name) return '—';
  const parts = String(name).replace(/[._-]/g, ' ').trim().split(/\s+/);
  return (parts[0][0] + (parts[1] ? parts[1][0] : '')).toUpperCase();
}

/* ---------------------------- icon sprite ---------------------------- */
const ICONS = {
  shield:   '<path d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3Z"/>',
  search:   '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  chevron:  '<path d="m9 6 6 6-6 6"/>',
  chevronD: '<path d="m6 9 6 6 6-6"/>',
  arrowUp:  '<path d="M12 19V5m0 0-6 6m6-6 6 6"/>',
  arrowDn:  '<path d="M12 5v14m0 0 6-6m-6 6-6-6"/>',
  sort:     '<path d="m7 10 5-5 5 5M7 14l5 5 5-5"/>',
  bell:     '<path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>',
  folder:   '<path d="M4 6a2 2 0 0 1 2-2h3.5l2 2H18a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z"/>',
  user:     '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  clock:    '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  check:    '<path d="m4 12 5 5L20 6"/>',
  checkC:   '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-5"/>',
  warn:     '<path d="M12 3 2 20h20L12 3Z"/><path d="M12 10v4m0 3h.01"/>',
  sun:      '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4"/>',
  moon:     '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"/>',
  refresh:  '<path d="M20 11a8 8 0 1 0-1.9 6"/><path d="M20 5v6h-6"/>',
  copy:     '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/>',
  spark:    '<path d="M12 3v4m0 10v4M3 12h4m10 0h4M5.6 5.6l2.8 2.8m7.2 7.2 2.8 2.8m0-12.8-2.8 2.8M8.4 15.6l-2.8 2.8"/>',
  doc:      '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z"/><path d="M14 3v5h5"/>',
  scale:    '<path d="M12 3v18M7 21h10M6 7h12M6 7 3 14h6L6 7Zm12 0-3 7h6l-3-7Z"/>',
  link:     '<path d="M10 13a5 5 0 0 0 7 0l2-2a5 5 0 0 0-7-7l-1 1"/><path d="M14 11a5 5 0 0 0-7 0l-2 2a5 5 0 0 0 7 7l1-1"/>',
  filter:   '<path d="M3 5h18l-7 8v6l-4 2v-8L3 5Z"/>',
  chart:    '<path d="M4 20V10m6 10V4m6 16v-7"/>',
  lock:     '<rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
};

/** Inline 24×24 stroke icon. `cls` is optional. */
function ico(name, cls = '') {
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
    stroke-linecap="round" stroke-linejoin="round"${cls ? ` class="${cls}"` : ''}
    aria-hidden="true">${ICONS[name] || ''}</svg>`;
}

/* ---------------------------- theme ---------------------------- */
(function initTheme() {
  const saved = localStorage.getItem('aml-theme');
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  document.documentElement.dataset.theme = saved || (prefersDark ? 'dark' : 'light');
})();

function toggleTheme() {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.setItem('aml-theme', next);
}

/* ---------------------------- app shell ---------------------------- */
/**
 * Renders the top bar. `active` is the nav key; `sub` is the contextual label.
 * Nav items intentionally match the current app exactly — two destinations.
 */
function renderAppbar({ active = 'queue', sub = '' } = {}) {
  return `
    <header class="appbar">
      <a class="brand" href="index.html">
        <span class="brand-mark">${ico('shield')}</span>
        <span class="brand-name">AML Alert Triage</span>
        ${sub ? `<span class="brand-sub">${esc(sub)}</span>` : ''}
      </a>
      <nav class="appnav">
        <a href="index.html" class="${active === 'queue' ? 'active' : ''}">Case Queue</a>
        <a href="demo.html" class="${active === 'demo' ? 'active' : ''}">Live Demo</a>
      </nav>
      <div class="appbar-spacer"></div>
      <div class="appbar-tools">
        <span class="env-chip"><i></i>UK · GBP · CORPORATE</span>
        <button class="icon-btn" type="button" onclick="toggleTheme()"
                title="Toggle light / dark" aria-label="Toggle light or dark theme">
          ${ico('sun', 'i-sun')}${ico('moon', 'i-moon')}
        </button>
      </div>
    </header>`;
}

function renderFooter(text) {
  return `<footer class="foot">${esc(text)}</footer>`;
}

/** Mounts shell chrome into #shell / #footer placeholders. */
function mountShell(opts, footerText) {
  const shell = document.getElementById('shell');
  if (shell) shell.outerHTML = renderAppbar(opts);
  const foot = document.getElementById('footer');
  if (foot) foot.outerHTML = renderFooter(footerText);
}

/* ---------------------------- toast ---------------------------- */
function toast(message, icon = 'checkC') {
  let host = document.querySelector('.toasts');
  if (!host) {
    host = document.createElement('div');
    host.className = 'toasts';
    document.body.appendChild(host);
  }
  const el = document.createElement('div');
  el.className = 'toast';
  el.innerHTML = `${ico(icon)}<span></span>`;
  el.querySelector('span').textContent = message;
  host.appendChild(el);
  setTimeout(() => {
    el.classList.add('out');
    el.addEventListener('animationend', () => el.remove(), { once: true });
  }, 2600);
}

/* ---------------------------- motion helpers ---------------------------- */
/** Animates a number from 0 to `to`. Cosmetic only. */
function countUp(el, to, ms = 850) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    el.textContent = to;
    return;
  }
  const start = performance.now();
  const tick = now => {
    const p = Math.min(1, (now - start) / ms);
    const eased = 1 - Math.pow(1 - p, 3);
    el.textContent = Math.round(to * eased);
    if (p < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

/** Lets width/dash transitions run by deferring the value to the next frame. */
function paintBars(root = document) {
  requestAnimationFrame(() => {
    root.querySelectorAll('[data-w]').forEach(el => { el.style.width = el.dataset.w; });
  });
}

function flashTarget(el) {
  if (!el) return;
  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  el.classList.remove('flash');
  void el.offsetWidth;
  el.classList.add('flash');
  setTimeout(() => el.classList.remove('flash'), 1400);
}

/** Highlights the section nav entry for whichever section is in view. */
function scrollSpy(navSel, sectionSel) {
  const links = [...document.querySelectorAll(navSel)];
  const sections = [...document.querySelectorAll(sectionSel)];
  if (!links.length || !sections.length) return;
  const io = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (!e.isIntersecting) return;
      links.forEach(a => a.classList.toggle('current', a.getAttribute('href') === '#' + e.target.id));
    });
  }, { rootMargin: '-15% 0px -70% 0px', threshold: 0 });
  sections.forEach(s => io.observe(s));
}

function copyText(text) {
  navigator.clipboard?.writeText(text).then(
    () => toast(`Copied ${text}`, 'copy'),
    () => toast('Copy failed', 'warn')
  );
}
