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

-- Apps suivies pour la détection MediaSession + templates de message (placeholders {name}/{subtitle}).
-- "source" dans now_playing correspond au package_name ici.
CREATE TABLE IF NOT EXISTS media_apps (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  package_name TEXT NOT NULL UNIQUE,
  label TEXT,                      -- nom affiché en gestion dans l'app (ex: "YouTube Music (ReVanced)")
  templates TEXT NOT NULL DEFAULT '[]', -- JSON array de strings, ex: ["Écoute {name}", "Se détend sur {name}"]
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
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

// Seed des apps suivies par défaut (une seule fois, si la table est vide) — prévoit les deux
// variantes (standard + patchée ReVanced) puisque tout le monde ne patch pas les mêmes apps.
const mediaAppsCount = db.prepare('SELECT COUNT(*) AS count FROM media_apps').get().count;
if (mediaAppsCount === 0) {
  const insert = db.prepare(
    'INSERT INTO media_apps (package_name, label, templates) VALUES (?, ?, ?)'
  );
  const defaults = [
    ['com.google.android.apps.youtube.music', 'YouTube Music', ['Écoute {name}', 'En train de kiffer {name}']],
    ['app.revanced.android.apps.youtube.music', 'YouTube Music (ReVanced)', ['Écoute {name}', 'En train de kiffer {name}']],
    ['com.google.android.youtube', 'YouTube', ['Regarde des vidéos sur YouTube']],
    ['app.revanced.android.youtube', 'YouTube (ReVanced)', ['Regarde des vidéos sur YouTube']],
    ['com.crunchyroll.crunchyroid', 'Crunchyroll', ['Regarde {name} sur Crunchyroll', 'En train de se détendre sur {name}']],
  ];
  for (const [packageName, label, templates] of defaults) {
    insert.run(packageName, label, JSON.stringify(templates));
  }
}
