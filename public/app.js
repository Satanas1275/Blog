async function loadFeed() {
  const feed = document.getElementById('feed');
  const res = await fetch('/api/posts?limit=30');
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
                .map((img) => `<img src="/uploads/${img}" loading="lazy" />`)
                .join('')}</div>`
            : ''
        }
      </article>`
    )
    .join('');
}

async function loadNowPlaying() {
  const el = document.getElementById('now-playing');
  try {
    const res = await fetch('/api/now-playing');
    const np = await res.json();
    if (!np || !np.title) {
      el.classList.add('hidden');
      return;
    }
    const label =
      np.source === 'youtube_music' ? '🎵 Écoute' : np.source === 'crunchyroll' ? '📺 Regarde' : '▶️ En ce moment';
    const content = np.link
      ? `<a href="${np.link}" target="_blank" rel="noopener">${escapeHtml(np.title)}</a>`
      : escapeHtml(np.title);
    el.innerHTML = `${label} : ${content}${np.subtitle ? ` — ${escapeHtml(np.subtitle)}` : ''}`;
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
  fetch('/api/analytics/ping', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ event_type: 'page_view', path: location.pathname }),
  }).catch(() => {});
}

loadFeed();
loadNowPlaying();
pingAnalytics();
setInterval(loadNowPlaying, 15000);
