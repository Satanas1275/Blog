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
| GET | `/api/posts` | non (+programmés si token) | Liste des posts publiés (le token révèle aussi les posts programmés) |
| POST | `/api/posts` | Bearer token | Créer un post (multipart: `content`, `images[]`, `published_at` optionnel) |
| PATCH | `/api/posts/:id` | Bearer token | Éditer le texte / la date d'un post |
| POST | `/api/posts/:id/images` | Bearer token | Ajouter des images à un post existant (multipart `images[]`) |
| DELETE | `/api/posts/:id/images/:filename` | Bearer token | Retirer une image précise d'un post |
| DELETE | `/api/posts/:id` | Bearer token | Supprimer un post (+ ses images) |
| GET | `/api/now-playing` | non | État actuel (pour le widget) |
| POST | `/api/now-playing` | Bearer token | Mettre à jour l'état (appelé par l'app), `image_base64` optionnel |
| GET | `/api/status` | non | Statut rapide actuel |
| POST | `/api/status` | Bearer token | Définir le statut rapide (écrase le précédent) |
| DELETE | `/api/status` | Bearer token | Effacer le statut rapide actuel |
| GET | `/api/media-apps` | non | Liste des apps suivies + leurs templates de message |
| POST | `/api/media-apps` | Bearer token | Ajouter une app suivie (`package_name`, `label`, `templates[]`) |
| PATCH | `/api/media-apps/:id` | Bearer token | Éditer une app suivie |
| DELETE | `/api/media-apps/:id` | Bearer token | Retirer une app suivie |
| POST | `/api/analytics/ping` | non | Événement de vue de page |
| GET | `/api/analytics/summary` | Bearer token | Stats agrégées (vues/jour, top pages, + `external` si configuré) |

### Templates de message (now-playing)
Chaque app suivie (`media_apps`) a une liste de messages avec placeholders `{name}` (titre détecté)
et `{subtitle}` (artiste/épisode). Le site choisit un message au hasard dans cette liste à chaque
affichage — ex: `["Écoute {name}", "En train de kiffer {name}"]`. Une app peut avoir un seul message
fixe sans placeholder (ex: YouTube : `"Regarde des vidéos sur YouTube"`).

## Configuration (.env)
| Variable | Défaut | Description |
|---|---|---|
| `APP_NAME` | `Bastian RIOT` | Nom affiché sur le site (titre + en-tête) |
| `BASE_PATH` | `/` | Sous-chemin de montage. `/blog` sert le site sur `monsite.com/blog` au lieu de `monsite.com/` |
| `LOGS_DIR` | `./logs` | Dossier des logs (voir plus bas) |
| `ANALYTICS_EXTERNAL_URL` | — | URL d'un service d'analytics externe, ex: `http://localhost:5055` |
| `ANALYTICS_EXTERNAL_TOKEN` | — | Token envoyé en `Authorization: Bearer` vers ce service |
| `ANALYTICS_TO` | `false` | Si `true`, chaque vue de page est aussi poussée vers le service externe |
| `ANALYTICS_FROM` | `false` | Si `true`, `/api/analytics/summary` va chercher des données chez le service externe et les inclut sous `external` |

### Contrat attendu du service d'analytics externe
- `POST {ANALYTICS_EXTERNAL_URL}/events` (Authorization: Bearer token) — reçoit
  `{ event_type, path, meta, timestamp }` à chaque ping (fire-and-forget, un échec n'affecte jamais le site)
- `GET {ANALYTICS_EXTERNAL_URL}/summary?days=N` (Authorization: Bearer token) — doit renvoyer un JSON
  arbitraire, repris tel quel sous la clé `external` de `/api/analytics/summary`

## Logs
Tout part dans `LOGS_DIR` (`./logs` par défaut, jamais commité) :
- `access.log` — logs HTTP (format `combined`, via morgan) : chaque requête, code, IP, user-agent
- `app.log` — événements applicatifs en JSON par ligne (démarrage, erreurs, échecs analytics externe...)
- `error.log` — sous-ensemble de `app.log`, uniquement les erreurs

Pas de rotation automatique (usage perso) — si les fichiers grossissent trop, `logrotate` classique
fait le travail, ou un `> logs/access.log` de temps en temps.

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

