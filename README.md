# Scribd Downloader — steVeR Edition (Android)

Application Android **native** (Kotlin + Jetpack Compose) convertie depuis l'extension Chrome
[`Scribd-Downloader-steVeR`](https://github.com/STEVERLAABS/Scribd-Downloader-steVeR) (MV3, v2.9.0).
Elle conserve **la même logique métier** : un unique flux via le service externe public
`scribd.vdownloaders.com`, avec le même autopilot (formulaire auto-rempli, attente, extraction du
lecteur final, **validation HEAD** du PDF), puis enregistrement via le gestionnaire de téléchargement
d'Android.

> **Usage de sauvegarde personnelle uniquement — respectez les droits des éditeurs.**
> L'app ne contourne **aucune protection** : si Cloudflare exige une vérification, c'est l'humain
> qui la complète (un tap), exactement comme dans l'extension. Si le service refuse (PDF
> indisponible, vide, corrompu), l'app affiche la raison précise — jamais de faux succès.

---

## Fonctionnement

1. Collez un lien Scribd (`scribd.com/document/…`, `/doc/…`, `/read/…`, `/book/…`, `/audiobook/…`).
2. Appuyez sur **⬇ Télécharger** — le reste est *automatique* :
   - étape 1 : analyse du lien (ID de document extrait, URL verbatim collée au service) ;
   - étape 2 : récupération via le service (le moteur visible exécute `autopilot.js`, le port
     1:1 de l'autopilot de l'extension) — si un captcha apparaît, la fenêtre moteur s'agrandit
     et vous cochez une seule case, le flux reprend seul ;
   - étape 3 : téléchargement par `DownloadManager` (progression en octets) ;
   - étape 4 : enregistrement dans le **Téléchargements** partagé.

Onglet **Bibliothèque** : ouvrir / partager / supprimer (Android 10+ pour la suppression).

**Permissions exigées : INTERNET uniquement** (ni stockage, ni notifications).

---

## Obtenir l'APK (3 voies)

### A. Android Studio (recommandé)
1. Installer [Android Studio Ladybug+](https://developer.android.com/studio) et le SDK Android 35.
2. `File > Open` → dossier `Scribd-Downloader-steVeR-Android`.
3. `Build > Build App Bundle(s) / APK(s) > Build APK(s)`.
4. APK : `app/build/outputs/apk/debug/app-debug.apk`.

### B. Ligne de commande
Prérequis : JDK 17, Gradle 8.11.1, SDK Android (plateforme 35 + build-tools 35.0.0).

```bash
cd Scribd-Downloader-steVeR-Android
echo "sdk.dir=/chemin/vers/SDK" > local.properties   # Windows : C:\\Users\\…\\Android\\Sdk
./gradlew assembleDebug                              # ou : gradle assembleDebug
./gradlew testDebugUnitTest                          # tests unitaires
```
(Les fichiers du wrapper ayant été régénérés, `gradle wrapper --gradle-version 8.11.1` peut être
nécessaire en local.)

### C. GitHub Actions (aucun outil local requis)
Pousser ce dossier sur un dépôt GitHub (branche `main`) → onglet **Actions** → workflow
**Build APK** → artefact `app-debug-apk` téléchargeable à chaque push.

---

## Installer sur le téléphone

1. Copier `app-debug.apk` sur le téléphone.
2. Autoriser **Installation d'applications inconnues** pour votre gestionnaire de fichiers
   (Paramètres > Applications > accès spéciaux).
3. Ouvrir l'APK > Installer.

---

## Notes honnêtes et limites

| Limite | Détail |
|---|---|
| Service externe tiers | Tout repose sur `scribd.vdownloaders.com` (+ lecteur `ilide.info`) ; sélecteurs/flux peuvent changer → messages d'erreur clairs, correctif local d'un fichier (`app/src/main/assets/autopilot.js`). |
| Cloudflare | Peut exiger une vérification humaine → un tap dans la fenêtre moteur ; garde de 180 s sinon erreur précise. |
| Documents refusés | `error_detected` / `error_damaged` / `error_timeout` : le service n'a pas ce PDF en original → réessayer plus tard. |
| App en arrière-plan | Pendant la préparation, l'app demande à rester au premier plan (keepScreenOn) ; le téléchargement lui-même survit à tout (DownloadManager + récepteur système). |
| Suppression/délès en bibliothèque | Android 10+ uniquement, via `DownloadManager.remove()` puis repli MediaStore ; si le fichier a disparu, message précis (pas de faux succès). |

## Structure

```
app/src/main/
├── assets/autopilot.js              # port 1:1 de injectAutopilot (caret et deltas documentés)
├── AndroidManifest.xml              # INTERNET seulement
└── java/com/stever/scribdownloader/
    ├── MainActivity.kt / ui/AppRoot.kt
    ├── engine/  UrlAnalyzer (validation+ID+slug), PdfValidator (HEAD), AutopilotEngine (WebView+bridge)
    ├── download/ Downloader (DownloadManager), DownloadCompleteReceiver, FileActions
    ├── data/    HistoryEntry/HistoryCodec/HistoryStore (SharedPreferences)
    └── ui/      DownloadScreen (4 étapes, moteur visible, captcha, erreurs) / LibraryScreen
```

## Crédits

- **Design, thème et droit** : © ing steVeR
- **Projet original** : [HugoAleOlguin/Scribd-Downloader-Premium](https://github.com/HugoAleOlguin/Scribd-Downloader-Premium)
