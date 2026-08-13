import { Router } from 'express';
import multer from 'multer';
import path from 'node:path';
import crypto from 'node:crypto';
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

// GET /api/posts — feed public, trié du plus récent au plus ancien
router.get('/', (req, res) => {
  const limit = Math.min(parseInt(req.query.limit) || 20, 100);
  const offset = parseInt(req.query.offset) || 0;
  const rows = db
    .prepare("SELECT * FROM posts WHERE published_at <= datetime('now') ORDER BY published_at DESC LIMIT ? OFFSET ?")
    .all(limit, offset);
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

export default router;
