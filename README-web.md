# Blog — Backend & Site (branche `web`)

## Setup
```bash
npm install
cp .env.example .env   # puis change AUTH_TOKEN pour un vrai secret
npm start
```

## Endpoints
| Méthode | Route | Auth | Description |
|---|---|---|---|
| GET | `/api/posts` | non | Liste des posts publiés |
| POST | `/api/posts` | Bearer token | Créer un post (multipart: `content`, `images[]`, `published_at` optionnel) |
| GET | `/api/now-playing` | non | État actuel (pour le widget) |
| POST | `/api/now-playing` | Bearer token | Mettre à jour l'état (appelé par l'app) |
| POST | `/api/analytics/ping` | non | Événement de vue de page |
| GET | `/api/analytics/summary` | Bearer token | Stats agrégées (vues/jour, top pages) |

## Sécurité
Toutes les routes d'écriture/lecture sensible demandent un header `Authorization: Bearer <AUTH_TOKEN>`.
Le token est à définir côté serveur (`.env`) et à copier dans l'app mobile (jamais commité).

## Déploiement (Hetzner VPS)
Prévu pour tourner derrière un reverse proxy (nginx/caddy) avec process manager (pm2/systemd).
`DB_PATH` et `UPLOADS_DIR` doivent pointer vers un volume persistant.
