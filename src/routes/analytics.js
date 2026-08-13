import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';

const router = Router();

// POST /api/analytics/ping — appelé par le site à chaque vue de page (pas de données perso stockées)
router.post('/ping', (req, res) => {
  const { event_type, path: p, meta } = req.body;
  db.prepare('INSERT INTO analytics_events (event_type, path, meta) VALUES (?, ?, ?)').run(
    event_type || 'page_view',
    p || null,
    JSON.stringify(meta || {})
  );
  res.status(204).end();
});

// GET /api/analytics/summary — protégé, pour la page analytique (site + app)
router.get('/summary', requireAuth, (req, res) => {
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

  res.json({ days, totalPosts, totalViews, viewsPerDay, topPaths });
});

export default router;
