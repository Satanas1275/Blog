import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const router = Router();

const UPLOADS_DIR = process.env.UPLOADS_DIR || './public/uploads';

// GET /api/now-playing — public, pour le widget du site
router.get('/', (req, res) => {
  const row = db.prepare('SELECT * FROM now_playing WHERE id = 1').get();
  res.json(row || null);
});

// POST /api/now-playing — protégé, appelé par l'app quand une MediaSession change.
// image_base64 (optionnel) : pochette/miniature envoyée par l'app (ex: album art MediaSession).
// Taille volontairement limitée côté app (vignette), donc base64 dans le JSON reste raisonnable.
router.post('/', requireAuth, (req, res) => {
  const { source, title, subtitle, link, state, image_base64 } = req.body;
  if (!source || !title) {
    return res.status(400).json({ error: 'source et title requis' });
  }

  let imageFilename = null;
  if (image_base64) {
    try {
      const buffer = Buffer.from(image_base64, 'base64');
      if (buffer.length > 5 * 1024 * 1024) {
        return res.status(400).json({ error: 'image trop grande (max 5 Mo)' });
      }
      fs.mkdirSync(UPLOADS_DIR, { recursive: true });
      imageFilename = `nowplaying-${crypto.randomUUID()}.jpg`;
      fs.writeFileSync(path.join(UPLOADS_DIR, imageFilename), buffer);
    } catch {
      return res.status(400).json({ error: 'image_base64 invalide' });
    }
  }

  // Si pas de nouvelle image envoyée, on garde l'ancienne plutôt que de l'effacer
  // (ex: l'app repush juste un changement d'état sans redécoder l'image à chaque fois).
  const previous = db.prepare('SELECT image FROM now_playing WHERE id = 1').get();
  const finalImage = imageFilename || previous?.image || null;

  db.prepare(
    `INSERT INTO now_playing (id, source, title, subtitle, link, image, state, updated_at)
     VALUES (1, ?, ?, ?, ?, ?, ?, datetime('now'))
     ON CONFLICT(id) DO UPDATE SET
       source=excluded.source, title=excluded.title, subtitle=excluded.subtitle,
       link=excluded.link, image=excluded.image, state=excluded.state, updated_at=excluded.updated_at`
  ).run(source, title, subtitle || null, link || null, finalImage, state || 'playing');

  res.json({ ok: true });
});

export default router;
