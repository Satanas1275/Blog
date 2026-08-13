# Blog — Backend & Site (branche `web`)

## Setup
```bash
npm install
cp .env.example .env   # puis change AUTH_TOKEN pour un vrai secret (port par défaut : 3003)
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

## Déploiement
Pas de reverse proxy à configurer côté serveur (nginx/caddy) — géré entièrement via Cloudflare
(voir `deploy/cloudflared-config.yml`, Cloudflare Tunnel). Le serveur écoute juste en local sur `3003`.

Domaine cible : **bastian-riot.rocknite-studio.com** (sous-domaine, pas de nom de domaine dédié).

- `deploy/cloudflared-config.yml` : config du tunnel, fait le lien
  `bastian-riot.rocknite-studio.com` → `http://localhost:3003` sans exposer de port.
- `deploy/blog-web.service` : unit systemd pour lancer le serveur en continu (`WorkingDirectory` et
  chemin `node` à adapter selon la machine).

### Si erreur au démarrage / à l'accès à la page d'accueil
Cause la plus probable sur Raspberry Pi (ARM) : `better-sqlite3` est un module natif qui doit être
compilé pour l'architecture de la machine au moment du `npm install`. Si le build échoue silencieusement
ou si les outils de compilation manquent, le serveur crash dès qu'il touche la DB (donc dès `/` ou
`/api/posts`). Vérifier :
```bash
npm install    # regarder si des erreurs node-gyp/python/make apparaissent
node -e "require('better-sqlite3')"   # doit s'exécuter sans erreur
```
Si besoin, installer les outils de build : `sudo apt install build-essential python3`.

Dès que t'as accès au RPi, envoie-moi le message d'erreur exact (log du `node src/server.js` ou de
`journalctl -u blog-web`) et je corrige direct plutôt que de deviner.

