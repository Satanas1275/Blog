import 'dotenv/config';
import express from 'express';
import morgan from 'morgan';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

import postsRouter from './routes/posts.js';
import nowPlayingRouter from './routes/nowPlaying.js';
import analyticsRouter from './routes/analytics.js';
import statusRouter from './routes/status.js';
import mediaAppsRouter from './routes/mediaApps.js';
import { logger, accessLogStream } from './logger.js';
import './db.js'; // initialise les tables au démarrage

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// BASE_PATH permet de servir le site ailleurs qu'à la racine (ex: "/blog" au lieu de "/").
// Toujours commence par "/", jamais de "/" final (sauf racine elle-même).
let BASE_PATH = process.env.BASE_PATH || '/';
if (!BASE_PATH.startsWith('/')) BASE_PATH = '/' + BASE_PATH;
if (BASE_PATH.length > 1 && BASE_PATH.endsWith('/')) BASE_PATH = BASE_PATH.slice(0, -1);

const APP_NAME = process.env.APP_NAME || 'Bastian RIOT';

const app = express();
app.use(morgan('combined', { stream: accessLogStream }));
app.use(express.json({ limit: '6mb' }));

const router = express.Router();

// Sert index.html à la racine du router avec templating (nom de l'app + base path injectés
// pour que app.js sache préfixer ses appels fetch si BASE_PATH n'est pas "/").
router.get('/', (req, res) => {
  const indexPath = path.join(__dirname, '..', 'public', 'index.html');
  fs.readFile(indexPath, 'utf8', (err, html) => {
    if (err) {
      logger.error('Impossible de lire index.html', { error: String(err) });
      return res.status(500).send('Erreur serveur');
    }
    const injected = html
      .replace(/\{\{APP_NAME\}\}/g, APP_NAME)
      .replace(/\{\{BASE_PATH\}\}/g, BASE_PATH === '/' ? '' : BASE_PATH)
      .replace(
        '</head>',
        `<script>window.__APP_NAME__=${JSON.stringify(APP_NAME)};window.__BASE_PATH__=${JSON.stringify(BASE_PATH === '/' ? '' : BASE_PATH)};</script></head>`
      );
    res.send(injected);
  });
});

router.use('/uploads', express.static(path.join(__dirname, '..', 'public', 'uploads')));
router.use(express.static(path.join(__dirname, '..', 'public')));

router.use('/api/posts', postsRouter);
router.use('/api/now-playing', nowPlayingRouter);
router.use('/api/analytics', analyticsRouter);
router.use('/api/status', statusRouter);
router.use('/api/media-apps', mediaAppsRouter);

router.get('/api/health', (req, res) => res.json({ ok: true }));

app.use(BASE_PATH, router);

// Erreurs non catchées ailleurs (ex: mauvais JSON, erreurs middleware) : loggées avant réponse générique
app.use((err, req, res, next) => {
  logger.error('Erreur non gérée sur une requête', { error: String(err), path: req.path, method: req.method });
  res.status(500).json({ error: 'Erreur serveur' });
});

process.on('uncaughtException', (err) => {
  logger.error('uncaughtException', { error: String(err), stack: err.stack });
});
process.on('unhandledRejection', (reason) => {
  logger.error('unhandledRejection', { reason: String(reason) });
});

const PORT = process.env.PORT || 3003;
app.listen(PORT, () => {
  logger.info('Serveur démarré', { port: PORT, basePath: BASE_PATH, appName: APP_NAME });
  console.log(`Blog server running on port ${PORT} (base path: ${BASE_PATH})`);
});
