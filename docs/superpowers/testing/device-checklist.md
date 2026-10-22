# Checklist essais sur appareil / émulateur

Exécuter chaque scénario sur un appareil réel (API 29+ recommandé). Marquer OK/NOK + note.

| # | Scénario | Attendu | OK/NOK |
|---|---|---|---|
| 1 | Lien valide `scribd.com/document/<id>/<slug>` → ⬇ Télécharger | Étapes 1→4 automatiques, PDF présent dans Téléchargements, nº doc affiché sous le champ | |
| 2 | URL invalide (`example.com`…) | Message `invalid_url`, aucun démarrage | |
| 3 | URL embeds `scribd.com/embeds/<id>/content` | URL normalisée en `scribd.com/doc/<id>` avant collage | |
| 4 | Cloudflare tourstile pendant le flux | Carte moteur s'agrandit + consigne ; un tap sur la case → flux reprend seul | |
| 5 | Captcha jamais complété (ne pas toucher, attendre 180 s) | `error_captcha_timeout` précis + Réessayer, PAS de boucle infinie | |
| 6 | Document refusé par le service (PDF indisponible) | `error_detected`/`error_damaged` affiché, aucun faux succès | |
| 7 | Serveur lent (attente > 90 s) | `error_timeout` ou `wait_timeout` affiché proprement | |
| 8 | Kill de l'app pendant le téléchargement (étape 3) | Le récepteur système complète l'entrée ; la bibliothèque affiche le PDF à la réouverture | |
| 9 | Bibliothèque : Ouvrir | Le PDF s'ouvre dans le visualiseur système | |
| 10 | Bibliothèque : Partager | Intent de partage avec le contenu du PDF | |
| 11 | Bibliothèque : Supprimer (API 29+) | Fichier supprimé ; ré-essai → message précis « introuvable » | |
| 12 | Même document téléchargé 2× | Noms distincts (`Nom.pdf`, `Nom_1.pdf`) sans écrasement | |
| 13 | Mode avion pendant les étapes 1-2 | `error_network` précis + Réessayer | |
| 14 | Écran verrouillé pendant la préparation | Consigne keepScreenOn ; en cas d'arrêt batterie : reprise propre à l'écran d'accueil | |

## Constat d'environnement (2026-10-22)

- La chaîne de build n'a **pas** pu être installée sur la machine d'exécution
  (téléchargements ~600 Mo expirés/0 B/s — connexion très lente) : les checkpoints ici
  ci-dessus restent la première étape de validation réelle du produit.
- Les tests JVM et `assembleDebug` sont eux vérifiés par le workflow GitHub Actions inclus.
