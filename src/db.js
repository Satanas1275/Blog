import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import 'dotenv/config';

const DB_PATH = process.env.DB_PATH || './data/blog.db';
fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

export const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

db.exec(`
CREATE TABLE IF NOT EXISTS posts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  content TEXT NOT NULL,
  images TEXT DEFAULT '[]',        -- JSON array of filenames
  published_at TEXT NOT NULL,      -- ISO 8601, choisi par l'utilisateur (défaut = maintenant)
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS now_playing (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  source TEXT,                     -- youtube_music | crunchyroll | youtube
  title TEXT,
  subtitle TEXT,                   -- artiste / épisode
  link TEXT,                       -- url si dispo
  image TEXT,                      -- filename de la pochette/miniature si dispo
  state TEXT,                      -- playing | paused | browsing
  updated_at TEXT
);

-- Statut rapide ("En voiture", "Dodo", ...) : une seule ligne, écrasée à chaque mise à jour.
-- Contrairement aux posts, ce n'est jamais archivé/affiché dans le feed.
CREATE TABLE IF NOT EXISTS status (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  label TEXT,
  updated_at TEXT
);

CREATE TABLE IF NOT EXISTS analytics_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_type TEXT NOT NULL,        -- page_view | post_view | now_playing_ping ...
  path TEXT,
  meta TEXT DEFAULT '{}',          -- JSON
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
`);

// Migration légère pour les DB déjà créées avant l'ajout de la colonne "image"
const nowPlayingCols = db.prepare("PRAGMA table_info(now_playing)").all().map((c) => c.name);
if (!nowPlayingCols.includes('image')) {
  db.exec('ALTER TABLE now_playing ADD COLUMN image TEXT');
}
