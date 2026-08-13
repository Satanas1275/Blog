import 'dotenv/config';

// Protège les routes d'écriture / analytics avec un simple Bearer token.
// Le token est défini côté serveur (AUTH_TOKEN) et doit être copié dans l'app mobile.
export function requireAuth(req, res, next) {
  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;

  if (!process.env.AUTH_TOKEN) {
    return res.status(500).json({ error: 'AUTH_TOKEN non configuré côté serveur' });
  }

  if (!token || token !== process.env.AUTH_TOKEN) {
    return res.status(401).json({ error: 'Non autorisé' });
  }

  next();
}
