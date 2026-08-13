import 'dotenv/config';
import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import postsRouter from './routes/posts.js';
import nowPlayingRouter from './routes/nowPlaying.js';
import analyticsRouter from './routes/analytics.js';
import './db.js'; // initialise les tables au démarrage

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();

app.use(express.json());
app.use('/uploads', express.static(path.join(__dirname, '..', 'public', 'uploads')));
app.use(express.static(path.join(__dirname, '..', 'public')));

app.use('/api/posts', postsRouter);
app.use('/api/now-playing', nowPlayingRouter);
app.use('/api/analytics', analyticsRouter);

app.get('/api/health', (req, res) => res.json({ ok: true }));

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Blog server running on port ${PORT}`));
