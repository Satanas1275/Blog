import { Router } from 'express';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';

const router = Router();

const serialize = (row) => ({ ...row, templates: JSON.parse(row.templates) });

// GET /api/media-apps — public : le site en a besoin pour choisir un message au hasard à l'affichage
router.get('/', (req, res) => {
  const rows = db.prepare('SELECT * FROM media_apps ORDER BY label COLLATE NOCASE').all();
  res.json(rows.map(serialize));
});

// POST /api/media-apps — protégé, ajoute une app suivie (package_name + templates)
router.post('/', requireAuth, (req, res) => {
  const { package_name, label, templates } = req.body;
  if (!package_name || !package_name.trim()) {
    return res.status(400).json({ error: 'package_name requis' });
  }
  const templatesArray = Array.isArray(templates) ? templates.filter((t) => t && t.trim()) : [];

  try {
    const info = db
      .prepare('INSERT INTO media_apps (package_name, label, templates) VALUES (?, ?, ?)')
      .run(package_name.trim(), label || null, JSON.stringify(templatesArray));
    res.status(201).json(serialize(db.prepare('SELECT * FROM media_apps WHERE id = ?').get(info.lastInsertRowid)));
  } catch (e) {
    if (String(e).includes('UNIQUE')) {
      return res.status(409).json({ error: 'Ce package_name est déjà suivi' });
    }
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

// PATCH /api/media-apps/:id — protégé, édite label/package_name/templates (partiel)
router.patch('/:id', requireAuth, (req, res) => {
  const app = db.prepare('SELECT * FROM media_apps WHERE id = ?').get(req.params.id);
  if (!app) return res.status(404).json({ error: 'App introuvable' });

  const { package_name, label, templates } = req.body;
  const newPackageName = package_name !== undefined ? package_name.trim() : app.package_name;
  const newLabel = label !== undefined ? label : app.label;
  const newTemplates = templates !== undefined
    ? JSON.stringify(templates.filter((t) => t && t.trim()))
    : app.templates;

  try {
    db.prepare('UPDATE media_apps SET package_name = ?, label = ?, templates = ? WHERE id = ?').run(
      newPackageName,
      newLabel,
      newTemplates,
      req.params.id
    );
    res.json(serialize(db.prepare('SELECT * FROM media_apps WHERE id = ?').get(req.params.id)));
  } catch (e) {
    if (String(e).includes('UNIQUE')) {
      return res.status(409).json({ error: 'Ce package_name est déjà suivi' });
    }
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

// DELETE /api/media-apps/:id — protégé
router.delete('/:id', requireAuth, (req, res) => {
  db.prepare('DELETE FROM media_apps WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

export default router;
