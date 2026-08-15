import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const router = Router();

const UPLOADS_DIR = process.env.UPLOADS_DIR || './public/uploads';

// Si l'app n'a rien renvoyé depuis ce délai, on considère que la lecture s'est arrêtée
// (app tuée par le système, réseau coupé...) même sans message explicite d'arrêt.
const STALE_MINUTES = parseInt(process.env.NOW_PLAYING_STALE_MINUTES) || 8;

// GET /api/now-playing — public, pour le widget du site. Périmé après STALE_MINUTES sans mise à jour.
router.get('/', (req, res) => {
  const row = db
    .prepare(`SELECT * FROM now_playing WHERE id = 1 AND updated_at >= datetime('now', ?)`)
    .get(`-${STALE_MINUTES} minutes`);
  res.json(row || null);
});

// POST /api/now-playing — protégé, appelé par l'app quand une MediaSession change, ou en heartbeat
// périodique (~5min) même sans changement, pour repousser l'expiration tant que ça joue toujours.
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

// DELETE /api/now-playing — protégé, appelé par l'app dès qu'elle détecte l'arrêt de la lecture
// (plus réactif que d'attendre l'expiration STALE_MINUTES)
router.delete('/', requireAuth, (req, res) => {
  db.prepare('DELETE FROM now_playing WHERE id = 1').run();
  res.json({ ok: true });
});

export default router;
