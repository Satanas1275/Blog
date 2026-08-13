import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';

const router = Router();

// GET /api/now-playing — public, pour le widget du site
router.get('/', (req, res) => {
  const row = db.prepare('SELECT * FROM now_playing WHERE id = 1').get();
  res.json(row || null);
});

// POST /api/now-playing — protégé, appelé par l'app quand une MediaSession change
router.post('/', requireAuth, (req, res) => {
  const { source, title, subtitle, link, state } = req.body;
  if (!source || !title) {
    return res.status(400).json({ error: 'source et title requis' });
  }

  db.prepare(
    `INSERT INTO now_playing (id, source, title, subtitle, link, state, updated_at)
     VALUES (1, ?, ?, ?, ?, ?, datetime('now'))
     ON CONFLICT(id) DO UPDATE SET
       source=excluded.source, title=excluded.title, subtitle=excluded.subtitle,
       link=excluded.link, state=excluded.state, updated_at=excluded.updated_at`
  ).run(source, title, subtitle || null, link || null, state || 'playing');

  res.json({ ok: true });
});

export default router;
