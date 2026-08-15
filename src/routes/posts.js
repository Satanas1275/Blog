import { Router } from 'express';
import multer from 'multer';
import path from 'node:path';
import crypto from 'node:crypto';
import fs from 'node:fs';
import { db } from '../db.js';
import { requireAuth } from '../auth.js';

const router = Router();

const UPLOADS_DIR = process.env.UPLOADS_DIR || './public/uploads';
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOADS_DIR),
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    cb(null, `${Date.now()}-${crypto.randomUUID()}${ext}`);
  },
});
const upload = multer({
  storage,
  limits: { fileSize: 15 * 1024 * 1024 }, // 15 Mo / image
  fileFilter: (req, file, cb) => {
    if (!file.mimetype.startsWith('image/')) return cb(new Error('Fichier non image refusé'));
    cb(null, true);
  },
});

// GET /api/posts — feed public (posts déjà publiés uniquement).
// Avec un token valide (Authorization: Bearer), retourne aussi les posts programmés dans le futur
// (utile pour l'app qui doit pouvoir les lister/éditer avant leur publication).
router.get('/', (req, res) => {
  const limit = Math.min(parseInt(req.query.limit) || 20, 100);
  const offset = parseInt(req.query.offset) || 0;

  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  const isAuthed = token && process.env.AUTH_TOKEN && token === process.env.AUTH_TOKEN;

  const query = isAuthed
    ? 'SELECT * FROM posts ORDER BY published_at DESC LIMIT ? OFFSET ?'
    : "SELECT * FROM posts WHERE datetime(published_at) <= datetime('now') ORDER BY published_at DESC LIMIT ? OFFSET ?";

  const rows = db.prepare(query).all(limit, offset);
  res.json(rows.map((r) => ({ ...r, images: JSON.parse(r.images) })));
});

// POST /api/posts — protégé par token. Accepte multipart (images[]) + champs texte.
// published_at optionnel : si absent, défaut = maintenant. Permet de programmer un post.
router.post('/', requireAuth, upload.array('images', 10), (req, res) => {
  const { content, published_at } = req.body;
  if (!content || !content.trim()) {
    return res.status(400).json({ error: 'content requis' });
  }

  const publishedAt = published_at
    ? new Date(published_at).toISOString()
    : new Date().toISOString();

  const images = (req.files || []).map((f) => f.filename);

  const info = db
    .prepare('INSERT INTO posts (content, images, published_at) VALUES (?, ?, ?)')
    .run(content.trim(), JSON.stringify(images), publishedAt);

  res.status(201).json({ id: info.lastInsertRowid, content, images, published_at: publishedAt });
});

// PATCH /api/posts/:id — protégé, édite le texte (et/ou la date) d'un post existant. Pas les images
// (gérées séparément ci-dessous, plus simple que de tout ré-uploader à chaque édition de texte).
router.patch('/:id', requireAuth, (req, res) => {
  const post = db.prepare('SELECT * FROM posts WHERE id = ?').get(req.params.id);
  if (!post) return res.status(404).json({ error: 'Post introuvable' });

  const { content, published_at } = req.body;
  const newContent = content !== undefined ? content.trim() : post.content;
  if (!newContent) return res.status(400).json({ error: 'content ne peut pas être vide' });
  const newPublishedAt = published_at ? new Date(published_at).toISOString() : post.published_at;

  db.prepare('UPDATE posts SET content = ?, published_at = ? WHERE id = ?').run(
    newContent,
    newPublishedAt,
    req.params.id
  );

  res.json({ ok: true });
});

// POST /api/posts/:id/images — protégé, ajoute une ou plusieurs images à un post existant
router.post('/:id/images', requireAuth, upload.array('images', 10), (req, res) => {
  const post = db.prepare('SELECT * FROM posts WHERE id = ?').get(req.params.id);
  if (!post) return res.status(404).json({ error: 'Post introuvable' });

  const existing = JSON.parse(post.images);
  const added = (req.files || []).map((f) => f.filename);
  const updated = [...existing, ...added];

  db.prepare('UPDATE posts SET images = ? WHERE id = ?').run(JSON.stringify(updated), req.params.id);
  res.json({ ok: true, images: updated });
});

// DELETE /api/posts/:id/images/:filename — protégé, retire une image précise d'un post
router.delete('/:id/images/:filename', requireAuth, (req, res) => {
  const post = db.prepare('SELECT * FROM posts WHERE id = ?').get(req.params.id);
  if (!post) return res.status(404).json({ error: 'Post introuvable' });

  const existing = JSON.parse(post.images);
  const updated = existing.filter((f) => f !== req.params.filename);

  db.prepare('UPDATE posts SET images = ? WHERE id = ?').run(JSON.stringify(updated), req.params.id);

  if (existing.includes(req.params.filename)) {
    fs.unlink(path.join(UPLOADS_DIR, req.params.filename), () => {}); // best-effort, on ignore l'erreur
  }

  res.json({ ok: true, images: updated });
});

// DELETE /api/posts/:id — protégé, supprime le post et ses images
router.delete('/:id', requireAuth, (req, res) => {
  const post = db.prepare('SELECT * FROM posts WHERE id = ?').get(req.params.id);
  if (!post) return res.status(404).json({ error: 'Post introuvable' });

  JSON.parse(post.images).forEach((filename) => {
    fs.unlink(path.join(UPLOADS_DIR, filename), () => {});
  });

  db.prepare('DELETE FROM posts WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

export default router;
