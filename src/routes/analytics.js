import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';
import { logger } from '../logger.js';

const router = Router();

const EXTERNAL_URL = process.env.ANALYTICS_EXTERNAL_URL || null;
const EXTERNAL_TOKEN = process.env.ANALYTICS_EXTERNAL_TOKEN || null;
const ANALYTICS_TO = process.env.ANALYTICS_TO === 'true';   // on pousse nos events vers le service externe
const ANALYTICS_FROM = process.env.ANALYTICS_FROM === 'true'; // on va chercher des données chez le service externe

// POST /api/analytics/ping — appelé par le site à chaque vue de page (pas de données perso stockées)
router.post('/ping', (req, res) => {
  const { event_type, path: p, meta } = req.body;
  const eventType = event_type || 'page_view';

  db.prepare('INSERT INTO analytics_events (event_type, path, meta) VALUES (?, ?, ?)').run(
    eventType,
    p || null,
    JSON.stringify(meta || {})
  );

  if (ANALYTICS_TO && EXTERNAL_URL) {
    fetch(`${EXTERNAL_URL}/events`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(EXTERNAL_TOKEN ? { Authorization: `Bearer ${EXTERNAL_TOKEN}` } : {}),
      },
      body: JSON.stringify({ event_type: eventType, path: p || null, meta: meta || {}, timestamp: new Date().toISOString() }),
    }).catch((e) => logger.warn('Échec du push analytics externe', { error: String(e) }));
  }

  res.status(204).end();
});

// GET /api/analytics/summary — protégé, pour la page analytique (site + app)
router.get('/summary', requireAuth, async (req, res) => {
  const days = Math.min(parseInt(req.query.days) || 30, 365);

  const viewsPerDay = db
    .prepare(
      `SELECT date(created_at) AS day, COUNT(*) AS count
       FROM analytics_events
       WHERE event_type = 'page_view' AND created_at >= datetime('now', ?)
       GROUP BY day ORDER BY day ASC`
    )
    .all(`-${days} days`);

  const topPaths = db
    .prepare(
      `SELECT path, COUNT(*) AS count
       FROM analytics_events
       WHERE event_type = 'page_view' AND path IS NOT NULL AND created_at >= datetime('now', ?)
       GROUP BY path ORDER BY count DESC LIMIT 10`
    )
    .all(`-${days} days`);

  const totalPosts = db.prepare('SELECT COUNT(*) AS count FROM posts').get().count;
  const totalViews = db
    .prepare(
      `SELECT COUNT(*) AS count FROM analytics_events
       WHERE event_type = 'page_view' AND created_at >= datetime('now', ?)`
    )
    .get(`-${days} days`).count;

  let external = null;
  if (ANALYTICS_FROM && EXTERNAL_URL) {
    try {
      const response = await fetch(`${EXTERNAL_URL}/summary?days=${days}`, {
        headers: EXTERNAL_TOKEN ? { Authorization: `Bearer ${EXTERNAL_TOKEN}` } : {},
      });
      if (response.ok) {
        external = await response.json();
      } else {
        logger.warn('Service analytics externe a répondu une erreur', { status: response.status });
      }
    } catch (e) {
      logger.warn('Échec du pull analytics externe', { error: String(e) });
    }
  }

  res.json({ days, totalPosts, totalViews, viewsPerDay, topPaths, external });
});

export default router;
