const BASE = window.__BASE_PATH__ || '';

async function loadFeed() {
  const feed = document.getElementById('feed');
  const res = await fetch(`${BASE}/api/posts?limit=30`);
  const posts = await res.json();

  feed.innerHTML = posts
    .map(
      (p) => `
      <article class="post">
        <time>${new Date(p.published_at).toLocaleString('fr-FR')}</time>
        <p>${escapeHtml(p.content)}</p>
        ${
          p.images.length
            ? `<div class="images">${p.images
                .map((img) => `<img src="${BASE}/uploads/${img}" loading="lazy" />`)
                .join('')}</div>`
            : ''
        }
      </article>`
    )
    .join('');
}

let mediaAppsCache = null;

async function getMediaApps() {
  if (mediaAppsCache) return mediaAppsCache;
  try {
    const res = await fetch(`${BASE}/api/media-apps`);
    mediaAppsCache = await res.json();
  } catch {
    mediaAppsCache = [];
  }
  return mediaAppsCache;
}

function fillTemplate(template, np) {
  return template.replace(/\{name\}/g, np.title || '').replace(/\{subtitle\}/g, np.subtitle || '');
}

async function loadNowPlaying() {
  const el = document.getElementById('now-playing');
  try {
    const res = await fetch(`${BASE}/api/now-playing`);
    const np = await res.json();
    if (!np || !np.title) {
      el.classList.add('hidden');
      return;
    }

    const apps = await getMediaApps();
    const config = apps.find((a) => a.package_name === np.source);
    let text;
    if (config && config.templates.length > 0) {
      const template = config.templates[Math.floor(Math.random() * config.templates.length)];
      text = fillTemplate(template, np);
    } else {
      text = np.title; // fallback si l'app détectée n'a pas (encore) de config côté site
    }

    const content = np.link
      ? `<a href="${np.link}" target="_blank" rel="noopener">${escapeHtml(text)}</a>`
      : escapeHtml(text);
    const img = np.image ? `<img src="${BASE}/uploads/${np.image}" alt="" />` : '';
    el.innerHTML = `${img}<span>${content}</span>`;
    el.classList.remove('hidden');
  } catch {
    el.classList.add('hidden');
  }
}

async function loadStatus() {
  const el = document.getElementById('status');
  try {
    const res = await fetch(`${BASE}/api/status`);
    const status = await res.json();
    if (!status || !status.label) {
      el.classList.add('hidden');
      return;
    }
    el.textContent = `💬 ${status.label}`;
    el.classList.remove('hidden');
  } catch {
    el.classList.add('hidden');
  }
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function pingAnalytics() {
  fetch(`${BASE}/api/analytics/ping`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ event_type: 'page_view', path: location.pathname }),
  }).catch(() => {});
}

loadFeed();
loadNowPlaying();
loadStatus();
pingAnalytics();
setInterval(loadNowPlaying, 15000);
setInterval(loadStatus, 15000);
setInterval(() => { mediaAppsCache = null; }, 60000); // permet de prendre en compte les changements de config
