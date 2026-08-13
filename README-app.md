# Rocknite Blog — App Android (branche `app`)

Kotlin + Jetpack Compose + Material 3.

## Config locale
Le projet n'a pas de `gradlew` commité (généré depuis un sandbox sans accès réseau vers
`services.gradle.org`). Pour développer en local :
```bash
gradle wrapper --gradle-version 8.7   # une fois, génère gradlew/gradlew.bat/gradle-wrapper.jar
```
Ou ouvre simplement le dossier dans Android Studio, qui le génère automatiquement.

## Ce qui est fait (v0)
- Navigation Compose : écran **Post** + écran **Analytique**
- Post : texte, sélection multi-photos, choix date/heure (défaut = maintenant → permet de programmer)
- Auth : token stocké chiffré (`EncryptedSharedPreferences`), envoyé en `Authorization: Bearer`
- Analytique : cartes stats + graphique vues/jour (Vico) + top pages
- `MediaWatcherService` (NotificationListenerService) : détecte les MediaSessions actives
  (YT Music / YouTube / Crunchyroll) et pousse titre/artiste vers `/api/now-playing`

## TODO (passe suivante)
- Patch ReVanced YouTube/YT Music pour broadcaster l'ID vidéo exact + l'état de navigation
  (home/vidéo/short) — plus fiable que les metadata MediaSession brutes, permet le lien direct
- Fallback lien Crunchyroll (pas de patch possible, appli fermée) : recherche par titre ou rien
- Écran de configuration (URL serveur + token) — pour l'instant à coder en dur / à ajouter
- Gestion des erreurs réseau plus robuste (retry/backoff) sur le service en fond
