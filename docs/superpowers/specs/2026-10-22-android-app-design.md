# Design — Application Android « Scribd Downloader — steVeR Edition »

Date : 2026-10-22
Statut : proposé (approche B validée par le partenaire humain)
Source de vérité logique : extension Chrome `Scribd-Downloader-steVeR` (MV3, v2.9.0) — dossier voisin `Desktop\Scribd-Downloader-steVeR\`

---

## 1. Intention et pourquoi

L'extension Chrome ne télécharge pas les documents Scribd par ses propres moyens : son unique
méthode active délègue à un service externe public (`scribd.vdownloaders.com`), en remplissant son
formulaire automatiquement, en suivant son flux (attente, éventuelle vérification Cloudflare
Turnstile **complétée manuellement par l'utilisateur, jamais contournée**), en extrayant l'URL PDF
directe du lecteur final (`ilide.info`, paramètre `?file=`), en la **validant par requête HEAD**
(type PDF + taille > 2 048 octets), puis en enregistrant le PDF.

L'objectif est de convertir cette logique en une **application Android native** :

- un champ où l'utilisateur colle un lien Scribd ;
- après soumission, **le moteur s'exécute en chaîne, automatiquement**, jusqu'à l'enregistrement du
  PDF via le gestionnaire de téléchargement Android — sans clic supplémentaire, sauf si le service
  exige la vérification humaine (Cloudflare), auquel cas un tap dans le moteur visible suffit et le
  flux reprend seul ;
- si le document n'est pas téléchargeable (refus du service, PDF vide/corrompu, délai dépassé),
  l'app **affiche une explication précise** — jamais de succès simulé.

## 2. Objectifs / Non-objectifs / Anti-objectifs

**Objectifs**
1. Validation stricte de l'URL et extraction de l'ID de document (mêmes réglages que
   `content.js/Utils.getDocumentId`).
2. Moteur d'automatisation porté quasi 1:1 depuis `background.js/injectAutopilot` vers un
   JavaScript exécuté dans un **WebView d'app** visible (carte « moteur »), piloté par une passerelle
   Kotlin/JS.
3. Enregistrement via `DownloadManager` (partagé Téléchargements, sans permission supplémentaire).
4. Écran « Bibliothèque » listant les PDF téléchargés : ouvrir / partager / supprimer.
5. UI Material 3 + Jetpack Compose, thème steVeR (dégradé violet/fuchsia `#A855F7 → #EC4899`).
6. Erreurs avec message précis + bouton « Réessayer », reprises identiques à `libs/i18n.js`.

**Non-objectifs**
- Aucun portage de la méthode « scan HQ » (html2canvas + jsPDF) : code mort dans l'édition steVeR,
  source de longues captures écran par écran ; l'app garde **une seule méthode**, fidèle à la
  logique métier actuelle de l'extension.
- Aucun re-affichage de la page Scribd dans l'app : seul le service externe est visité.
- Pas de compte utilisateur, pas de paramètres persistants complexes (langue auto par locale).

**Anti-objectifs (échec même si ça « fonctionne »)**
- Contourner Cloudflare Turnstile, un paywall, une protection de document ou DRM. La vérification
  humaine reste interactive, au même niveau de consentement que dans l'extension.
- Simuler une réussite : tout état « Terminé » correspond à un fichier réellement enregistré et
  vérifiable.
- Demander des permissions superflues (stockage, notifications…).

## 3. Contraintes et limites honnêtes (à assumer publiquement dans l'app)

1. **Dépendance à un service tiers.** Tout repose sur `scribd.vdownloaders.com` (+ lecteur
   `ilide.info`). Sélecteurs et flux peuvent changer sans préavis ; l'app doit dégrader proprement
   (messages explicites) et les sélecteurs doivent rester factorisés dans un fichier unique
   (`assets/autopilot.js`) faciles à corriger.
2. **Cloudflare Turnstile.** En cas de challenge, le WebView s'agrandit et l'utilisateur tape la
   case ; l'autopilot reprend ensuite seul, exactement comme dans l'extension
   (états `verify_manual` / `verify_done`).
3. **Accès autorisé seulement.** Si le service refuse (document non disponible, PDF vide
   `error_damaged`, timeout `error_timeout`), l'app affiche la raison et propose « Réessayer ».
4. Usage personnel de sauvegarde ; respect des droits des éditeurs (mention affichée).

## 4. Approches considérées

| | Principe | Verdict |
|---|---|---|
| A. HTTP pur (OkHttp/Jsoup, invisible) | Répliquer le flux vdownloaders en requêtes réseau | Rejetée : Turnstile bloquera un client headless → échecs systématiques ; ce serait simulé/dishonnête |
| **B. WebView moteur (Compose)** — **retenue** | Le WebView exécute l'autopilot JS porté de `injectAutopilot` ; carte visible pendant le traitement ; la page reste manipulable si un captcha apparaît | Fidèle, gère le captcha honnêtement, automatique à ~100 % depuis la soumission |
| C. Hybride A+B | Chemin HTTP rapide puis repli WebView | Rejetée : le chemin HTTP finira tôt ou tard devant Turnstile → complexité inutile |

## 5. Architecture retenue

```
Scribd-Downloader-steVeR-Android/
├── README.md                          (instructions FR complètes : build APK, install, limites)
├── .github/workflows/build-apk.yml    (génération d'APK sur GitHub Actions)
├── settings.gradle.kts  build.gradle.kts  gradle.properties
├── gradle/libs.versions.toml  +  gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts  proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/            (values: EN par défaut + values-fr ; icônes adaptatives vectorielles)
        ├── assets/autopilot.js
        └── java/com/stever/scribdownloader/
            ├── MainActivity.kt            (Mono-activité Compose + Navigation)
            ├── ui/Theme.kt DownloadScreen.kt LibraryScreen.kt WebViewCard.kt
            ├── engine/UrlAnalyzer.kt      (validation + docId + slug)
            ├── engine/AutopilotEngine.kt  (cycle de vie WebView + bridge JS↔Kotlin)
            ├── engine/PdfValidator.kt     (HEAD HttpURLConnection, miroir validate_download)
            ├── download/Downloader.kt     (DownloadManager : enqueue + suivi progression)
            ├── download/DownloadCompleteReceiver.kt   (manifest-registered)
            ├── data/HistoryStore.kt       (SharedPreferences + org.json, zéro dépendance)
            └── ui/DownloadViewModel.kt    (StateFlow<UiState>)
```

### Pile technique et versions (mes choix, cohérents entre eux)

- **Kotlin 2.0.21** (+ `org.jetbrains.kotlin.plugin.compose`), **AGP 8.7.3**, **Gradle 8.11.1**,
  JDK 17, `compileSdk = targetSdk = 35`, **`minSdk = 24`**.
- Jetpack **Compose BOM 2024.12.01**, Material 3, `lifecycle 2.8.7`, `navigation-compose 2.8.5`,
  `activity-compose 1.9.3`, `core-ktx 1.15.0`.
- **Aucune bibliothèque réseau tierce** (HEAD via `HttpURLConnection` : miroir fidèle de
  `validate_download` qui utilisait `fetch(..., {method:'HEAD'})`).
- **Pas de Room/WorkManager/Gson** : historique en `SharedPreferences` + `org.json` (le volume de
  données est faible, la dépendance est minimale ; remplaçable sans impact produit).
- JUnit 4 pour les tests unitaires purs (analyse d'URL, sanitisation du nom de fichier).

### Permissions : `INTERNET` uniquement

- Stockage : aucun (DownloadManager écrit dans le Téléchargements partagé lui-même ; MediaStore
  `Downloads` API 29+ pour ouvrir/partager via `content://`, `FileProvider` en repli sur API 24–28).
- Notifications : aucune (progression 100 % in-app ; `statusBarHidden` sur la requête).
- Suppression : masquée sur API < 29 (pas de `WRITE_EXTERNAL_STORAGE`) ; sur API ≥ 29 via
  `ContentResolver.delete` de l'URI média possédée par l'app — zéro permission.
- Presse-papiers : lecture via ClipboardManager, sans permission (usage foreground standard).

### URL collée au service (contrat de `UrlAnalyzer`)

- **Règle par défaut** : l'URL **telle que soumise par l'utilisateur** est collée dans le formulaire
  du service — c'est l'équivalent exact de `AppState.cachedUrl = window.location.href` dans
  `content.js/launchBridgeDownload` (l'extension collait l'URL de la page de document visitée ;
  ici l'utilisateur fournit cette URL directement).
- **Exception : liens d'embed** (`/embeds/{id}/...`) : jamais collés tels quels dans l'extension.
  Ils sont normalisés en `https://www.scribd.com/doc/{id}` avant collage.
- **Liens rejetés** (téléchargement impossible sans quitter l'app) : URLs non-scribd, URLs
  Scribd sans aucun ID de document, URLs de collection/liste. Message précis du pourquoi + champ
  rééditable.
- Vérification build-time : confirmer sur la page du service les formes d'URL acceptées (en
  particulier slugless `/doc/{id}`) ; si le service ne couvre pas une forme approuvée ici, le
  normalisateur construit la forme canonique correcte — jamais de contournement.

### Moteur : portage de `injectAutopilot` (mes décrits précis d'implémentation)

`assets/autopilot.js` reprend le code de `background.js/injectAutopilot` avec ces adaptations :

1. **Pas de HUD injecté** (`updateStatusUI`) — l'app affiche l'état nativement ; le script ne fait
   que **rapporter** via `SteVeRBridge.post(json)` (interface JS annotée, transitions sur main
   thread) : `{event:"status", step, key}`, `{event:"captcha"}`, `{event:"pdf", url}`,
   `{event:"failed", key}`, `{event:"timeout", key}`.
2. **Phases identiques, dans le même ordre** (`onPageFinished` + intervalle au fil de la page) :
   - `ilide.info` → ≥45 s : détecteur d'erreur (`#errorContainer, .error-container, [class*="error"]`
     visible avec texte) puis lecture `iframe[src*="viewer.html"]` → `?file=` décodé →
     `looksPdf` (`.pdf` ou contient `download`).
   - Page d'attente vdownloaders (`Please wait a moment` / `ready in` / `#btn-download`) → poll
     500 ms × 180 ; au lien `ilide` : navigation immédiate.
   - Formulaire (`#url` / `input[name=url]` + `button[type=submit].btn-primary`, absent si
     `.cf-turnstile` visible) → focus, EVENT `input`, click à ~800 ms.
   - Phase captcha : `[name=cf-turnstile-response]` pollé (800 ms) ; événement `captcha` dès que
     boîte visible ; click du bouton dès résolution ; ~45 s sans résolution → état « action
     requise » (l'utilisateur tape la case dans le WebView — **aucune automatisation du challenge**).
3. **Validations Kotlin** : à `pdf` reçu → `PdfValidator` (HEAD, `content-type` ∈ pdf/octet/
   force-download, taille > 2 048 ou inconnue) puis `DownloadManager` → étape 3/4 ;
   échec → message `error_damaged` (PDF vide/indisponible).
4. **Sécurités** : `addJavascriptInterface` limité à 1 interface typée ; navigation top-level
   restreinte aux hôtes du flux (`vdownloaders.com`, `ilide.info`) — les sous-ressources restent
   libres ; UA : **non modifié** (pas d'usurpation).
5. Anti double-injection (garde `window.__spdEngineV1`) ; état annulable depuis l'UI.
6. **Captcha devant le formulaire** (angle mort hérité) : si Turnstile est visible alors qu'un
   formulaire est encore à remplir, l'app affiche l'état « action requise » **puis, une fois la case
   complétée par l'utilisateur**, ré-exécute la phase formulaire normalement (chaînage au lieu du
   blocage silencieux de l'extension). Aucune automatisation du challenge.
7. **Surveillance d'inactivité captcha** : au-delà de 180 s sans résolution du challenge, l'état
   passe en `captcha_timeout` (carte erreur précise + « Réessayer ») au lieu de poller à l'infini.
   Le bouton « Annuler » reste disponible à tout moment.

### Machine à états de l'UI (4 étapes fixes, comme le `step/4` de l'extension)

```
Idle ──submit──▶ S1 AnalyseDuLien (UrlAnalyzer: valide? docId? titre?)
   S1 ──ok──▶ S2 Récupération (S2a formulaire → S2b captcha(manuel) → S2c attente-lien →
              S2d ilide → S2e validation HEAD)
   S2e ──ok──▶ S3 Téléchargement (DownloadManager, progression octets/total en déterminé)
   S3 ──ok──▶ S4 Enregistré (historique + ouvrir/partager)
   toute étape ──échec──▶ Erreur(détail précis, « Réessayer »)
```

Clés de messages reprises/traduites depuis `libs/i18n.js` : `init, verify, verify_manual,
verify_done, wait, wait_timeout, redirect, analyzing, validating, success, error_detected,
error_damaged, error_timeout`, avec adaptations FR/EN — les mentions « Smart Scan (HQ) »
(inexistante ici) sont remplacées par « Réessayer » ou fermer/relancer.

### Écrans

- **Accueil (« Télécharger »)** : bandeau dégradé steVeR + logo « S » ; champ URL avec bouton
  « Coller » (presse-papiers) et validation live (ID de document reconnu affiché sous le champ) ;
  bouton principal « ⬇ Télécharger » ; carte étapes 1→4 avec barre de progression (déterminée
  pendant la phase 3, ondulée pendant la phase 2 pour ces valeurs de phase) ; à `captcha` la carte
  WebView s'étend (~55 % écran) avec consigne ; bouton « Annuler » pendant l'exécution.
- **Bibliothèque** : liste (nom, taille, date, statut) ; actions **Ouvrir** (ACTION_VIEW),
  **Partager** (ACTION_SEND), **Supprimer** (API ≥ 29) ; état vide soigné ; note de crédits et
  d'usage personnel sur un pied d'écran discret.
- Langue : `values/` = **EN défaut** + `values-fr/` complet (convention Android ; téléphone en
  français → application en français).

### Réception d'achèvement

`DownloadCompleteReceiver` (manifest) : met à jour l'historique même si l'app a été mise en arrière-
plan pendant l'attente du téléchargement ; reprise correcte en cas de mort du process (l'état du
moteur est perdu → l'écran d'accueil repart proprement à « Erreur/Reprendre » avec l'URL conservée
dans l'historique « en cours »).

### Posture pendant la phase 2 (premier plan requis)

Anti-objectif enregistré : pas de permissions superflues ni de notification système → **pas de
foreground service**. Pendant S2, l'app pose `keepScreenOn` sur l'écran du moteur et affiche la
consigne héritée de l'extension (« laissez cette app au premier plan pendant la préparation »).
Si le WebView est figé en arrière-plan (timers throttlés), le retour au premier plan relance le
cycle de détection ; une absence de progression détectée à la reprise repart de `S2a` proprement.
Le téléchargement (S3) survit à tout car il est porté par `DownloadManager`.

### Suppression en bibliothèque (mécanisme corrigé par la relecture)

L'historique persiste **à la fois** l'`downloadId` DownloadManager et le nom de fichier/URI.
Suppression d'un élément (API ≥ 29) :
1. primaire : `DownloadManager.remove(downloadId)` — supprime l'enregistrement et le fichier,
   zéro permission ;
2. si l'enregistrement n'existe plus : suppression `ContentResolver.delete` sur l'URI MediaStore
   de l'app ;
3. si aucun n'aboutit : message précis « fichier introuvable / déjà supprimé manuellement » et
   re-synchronisation de l'entrée (statut « expiré »), jamais de faux succès.
**Test device tôt dans le plan** : vérifier les 3 issues sur API 29+ avant de câbler S4.

## 6. Ce qui reste au builder

- Vérifier au moment du build la page `https://scribd.vdownloaders.com/` (sélecteurs `#url`,
  bouton submit, typo des pages d'attente) via un simple fetch ; corriger `autopilot.js` si dérive.
- Polissage pixel (espacements, états autofocus, animations de transition) et réglage fin des
  textes.
- Génération des assets vectoriels (icône adaptative, splash léger) — aucune image binaire à
  récupérer en ligne.
- Tests unitaires `UrlAnalyzer` (cas doc/document/read/book/audiobook/embeds, URLs invalides,
  locale, query/fragment) + sanitisation de nom de fichier.

## 7. Build et livraison

- **Android Studio (Ladybug+)** : ouvrir le dossier → Sync → Run → **Build > Build APK(s)**.
- **CLI** : `./gradlew assembleDebug` (wrapper régénéré / distribué) ;
  release : `assembleRelease` + signature via `keystore` (marche illustrée dans le README).
- **CI** : workflow GitHub Actions Ubuntu + JDK 17 (Temurin) + Gradle 8.11.1 qui publie
  `app-debug.apk` en artefact téléchargeable.

## 8. Risques

1. Dérive des sélecteurs du service externe → messages explicites + correctif local d'un seul
   fichier (`autopilot.js`).
2. Turnstile plus agressif en WebView → note honnête dans « Erreur » (faire une nouvelle tentative)
   ; jamais de contournement.
3. Lien URL protégé par le serveur (expiration) → nouveau cycle complet depuis l'étape 2 en appuyant
   « Réessayer » (nouvelle requête service).
4. Stockage : FileProvider paths limités aux fichiers produits par l'app.

## 9. Décisions tranchées sans consultation (veto possible)

| Décision | Raison |
|---|---|
| Une seule méthode (l'externe), pas de scan HQ | Édition steVeR = flux unique ; secret de la conversion |
| Zéro permission hors `INTERNET` | Requis « pas de permissions inutiles » ; DownloadManager suffit |
| Pas de service au premier plan ; `keepScreenOn` pendant la phase 2 | Le téléchargement (S3) survit via DownloadManager ; un foreground service violerait « pas de permissions inutiles » et exigerait une notification système |
| Historique via `SharedPreferences`+`org.json` | Petit volume, zéro dépendance, remplaçable par Room |
| EN défaut + `values-fr` | Convention Android ; téléphone FR → app FR automatiquement |
| `minSdk 24` | Couvre ~98 % des appareils, backport FileProvider géré |
| Pas de notification système, progression in-app | Pas de permission 13+, progression visible en jeu |
| Suppression : `DownloadManager.remove()` primaire, replis MediaStore/message précis | Fichiers écrits par le DownloadProvider ne sont pas toujours délétables comme « possédés par l'app » |
| Chaînage captcha + garde d'inactivité | Corrige un blocage silencieux hérité de l'extension sans automatiser le challenge (toujours complété par l'humain) |
