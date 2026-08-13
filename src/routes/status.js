import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';

const router = Router();

// GET /api/status — public, pour l'affichage sur le site
router.get('/', (req, res) => {
  const row = db.prepare('SELECT * FROM status WHERE id = 1').get();
  res.json(row || null);
});

// POST /api/status — protégé, appelé par l'app pour un statut rapide ("En voiture", "Dodo"...)
// Écrase le précédent : ce n'est pas un post, pas d'historique conservé.
router.post('/', requireAuth, (req, res) => {
  const { label } = req.body;
  if (!label || !label.trim()) {
    return res.status(400).json({ error: 'label requis' });
  }

  db.prepare(
    `INSERT INTO status (id, label, updated_at)
     VALUES (1, ?, datetime('now'))
     ON CONFLICT(id) DO UPDATE SET label=excluded.label, updated_at=excluded.updated_at`
  ).run(label.trim());

  res.json({ ok: true });
});

// DELETE /api/status — protégé, pour effacer le statut courant
router.delete('/', requireAuth, (req, res) => {
  db.prepare('DELETE FROM status WHERE id = 1').run();
  res.json({ ok: true });
});

export default router;
