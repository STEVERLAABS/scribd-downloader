# Android App « Scribd Downloader — steVeR Edition » — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Appli Android native (Kotlin + Compose) qui télécharge automatiquement un PDF Scribd depuis l'URL collée par l'utilisateur, via le même flux de service externe que l'extension steVeR.

**Architecture:** Mono-activité Compose (Material 3) + moteur WebView exécutant `assets/autopilot.js` (port 1:1 de `injectAutopilot` de `background.js`), passerelle JS↔Kotlin typée, validation HEAD en Kotlin pur, enregistrement via `DownloadManager`, historique en `SharedPreferences`+`org.json`.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3, Gradle 8.11.1, minSdk 24 / target 35, Permission = INTERNET uniquement.

**Spec:** `Scribd-Downloader-steVeR-Android/docs/superpowers/specs/2026-10-22-android-app-design.md`
**Logique d'origine:** `Desktop/Scribd-Downloader-steVeR/background.js` (`injectAutopilot`), `content.js` (`Utils`, `sanitizeFilename`), `libs/i18n.js`.

## Global Constraints

- Versions exactes : Kotlin `2.0.21` (+ plugin compose `2.0.21`), AGP `8.7.3`, Gradle `8.11.1`, compileSdk/targetSdk `35`, minSdk `24`, JDK 17 (Temurin).
- BOM Compose `2024.12.01` ; `lifecycle-*-compose 2.8.7` ; `navigation-compose 2.8.5` ; `activity-compose 1.9.3` ; `core-ktx 1.15.0` ; `kotlinx-coroutines-android 1.9.0` ; JUnit `4.13.2`. **Aucune autre dépendance** (ni OkHttp, ni Room, ni Gson).
- Permissions manifest : **`INTERNET` seulement**. Pas de notifications, pas de storage.
- applicationId `com.stever.scribdownloader` ; versionName `1.0.0-steVeR` ; versionCode `1`.
- Layout Mode 3 : `dynamicColor = false`, palette violette fixe (primaire `#A855F7`, secondaire `#EC4899`).
- Chaînes : `values/` = EN, `values-fr/` = FR complet.
- Le texte googlé « From the extension » : tout ce qui est un portage doit refléter le comportement du fichier source indiqué, pas une réinvention.
- Commits fréquents ; repo git local initialisé dans `Scribd-Downloader-steVeR-Android/`.
- Anti-objectifs tables : jamais contourner Cloudflare, ni simuler un succès (chaque état « saved » provient d'un fichier réel).

## Review Focus

1. **URL du service rejetée alors qu'elle est humainement valide** (p. ex. lien sans slug) → l'app doit coller l'URL verbatim et laisser le service décider ; comportement testé par `UrlAnalyzerTest` (tâche 4).
2. **Captcha jamais résolu** → termine par `captcha_timeout` (180 s) avec carte erreur, PAS une boucle infinie ni un succès simulé (tâche 8, garde d'inactivité ; test manuel coût : connaître la valeur).
3. **Fichier « téléchargé » alors que le HEAD a dit non** → `error_damaged` précis, branché sur l'événement `PdfFound` non éligible (tâche 6, `PdfValidatorTest`).
4. **UI affichant un fichier qui n'existe plus** → bibliothèque avec suppression/reouverture précises, jamais de crash (tâche 11, test device API 29+ tôt).
5. **Noms de fichiers TOUJOURS uniques et valides Windows/android** → `uniquify` est testée (tâche 7).

---

### Task 1: Chaîne de build locale (JDK 17 + SDK Android + Gradle)

**Files:**
- Create (hors-repo): `%LOCALAPPDATA%\android-cli\{jdk17,gradle-8.11.1,android-sdk}`
- Create: `Scribd-Downloader-steVeR-Android/.gitignore`

**Interfaces:**
- Produces: commands « gradle » utilisables dans un shell PowerShell avec `JAVA_HOME` et `ANDROID_HOME` ; utilisé par toutes les step « Run » des tâches suivantes.

- [ ] **Step 1: git init + .gitignore**

```powershell
git init
```

`.gitignore` : `.gradle/`, `build/`, `local.properties`, `*.iml`, `captures/`, `.idea/`.

- [ ] **Step 2: Installer JDK 17 (Temurin), Gradle 8.11.1, cmdline-tools**

Téléchargements (zip → `%TEMP%\opencode`) :
- JDK : `https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse`
- Gradle : `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip`
- cmdline-tools : `https://dl.google.com/android/repository/commandlinetools-win-13114758-latest.zip`

Extraire vers `%LOCALAPPDATA%\android-cli\` (JAVA_HOME=jdk17, GRADLE_HOME=gradle-8.11.1). sdk dans `android-sdk` ; y placer `cmdline-tools\latest\bin` correctement renommé. Dossiers déplacés dans `android-cli` pour persister hors Temp.

- [ ] **Step 3: Licences SDK sans service interactif**

Créer `android-sdk\licenses\android-sdk-license` (2 lignes : `24333f8a63b682575c74361f4962b3abedb85cde` + `d56f5187479451eabf01ba78edc9dc56775144cf`) puis :

```powershell
sdkmanager --sdk_root=$env:LOCALAPPDATA\android-cli\android-sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

- [ ] **Step 4: Wrapper gradle dans le projet**

```powershell
cd Scribd-Downloader-steVeR-Android
gradle wrapper --gradle-version 8.11.1
```

Expected: `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar|properties` créés ; `gradle --version` → `JVM: 17.x`.

- [ ] **Step 5: Commit**

```bash
git add .gitignore && git commit -m "chore: bootstrap build toolchain (gitignore)"
```

---

### Task 2: Squelette Gradle compilable + activité Compose vide

**Files (Create):**
- `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`, `gradle/libs.versions.toml`
- `app/build.gradle.kts`, `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml` (permission INTERNET, activité MainActivity exported)
- `app/src/main/java/com/stever/scribdownloader/MainActivity.kt` (Text « steVeR » centré, `enableEdgeToEdge()`)
- `app/src/main/res/values/{strings.xml,themes.xml,colors.xml}` + `values-night` minimal
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `res/drawable-{ic_launcher_background.xml, ic_launcher_foreground.xml}` (vector : dégradé violet + flèche download `M19,9h-4V3H9v6H5l7,7,7-7ZM5,18v2h14v-2H5z`)

**Interfaces:**
- Produces: projet qui **`.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL** et `app-debug.apk` généré ; versions toml figées ; `Icons.Filled.*` (material-icons-extended) disponibles ; `Lint` `abortOnError = false`.

- [ ] **Step 1: Écrire les fichiers Gradle (versions Global Constraints)**
- [ ] **Step 2: Manifest min + MainActivity"
- [ ] **Step 3: Build & vérifier**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` ; `app/build/outputs/apk/debug/app-debug.apk` existe.

- [ ] **Step 4: Commit** `feat: compilable Compose skeleton`

---

### Task 3: Palette steVeR + thème Material3

**Files (Create):** `ui/theme/{Color.kt, Type.kt, Theme.kt}`
**Interfaces:** `SteVeRTheme(dark: Boolean, content)`, couple `SteVeRColors` : primary `#A855F7`, secondary `#EC4899`, surface dark `#121015`, gradient line précisée au design doc (#a855f7 → #ec4899, 135°).
- [ ] **Step 1:** implémenter consultable, hooké dans MainActivity
- [ ] **Step 2:** build ok + commit `feat: steVeR theme`

---

### Task 4: `UrlAnalyzer` + tests unitaires (TDD)

**Files:**
- Create: `engine/UrlAnalyzer.kt`, Test: `app/src/test/java/com/stever/scribdownloader/engine/UrlAnalyzerTest.kt`

**Interfaces:** (le contrat suivant est fixe pour les tâches 9-10)
```kotlin
data class AnalyzedUrl(val docId: Long, val pasteUrl: String, val kind: UrlKind)
enum class UrlKind { DOCUMENT, READ, BOOK, AUDIOBOOK, EMBEDS }
object UrlAnalyzer {
  fun analyze(raw: String): AnalyzedUrl?   // null = invalide
  fun sanitizeFileName(name: String, docId: Long): String
}
```

- [ ] **Step 1: le test FAIL** — assertions clés (toutes dans le test) : « https://www.scribd.com/document/123/xyz » → docId 123 pasteUrl verbatim ; « https://es.scribd.com/doc/123 » → idem verbatim ; « /embeds/123/content?x=1 » → kind EMBEDS + pasteUrl `https://www.scribd.com/doc/123` ; `/read/456/abc` , `/book/789`, `/audiobook/1` fine ; URL `http://` upgrade https ; query/fragment tolerés ; rejets : non-scribd, sous-domaine non-scribd (regex d'hôte : hôte == `scribd.com` ou suffixe `.scribd.com`), aucune ID d'ID interne, chaîne vide. `sanitizeFileName`: « *Ca/fé?? : Title_ » → « _Cafe_Title_ » ( même jeu [a-zA-Z0-9\ \-\_ À-ÿ] collapse, trim, cap 120, fallback `Scribd_{docId}` ).
- [ ] **Step 2:** Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*UrlAnalyzer*"` EXPECTED: FAIL (compile: symbols missing)
- [ ] **Step 3:** implémenter (regex identiques de `Utils.getDocumentId` : `(?:doc|document|embeds|read|book|audiobook)/(\d+)`)
- [ ] **Step 4:** Run: PASS
- [ ] **Step 5:** commit `feat: UrlAnalyzer with tests`

---

### Task 5: `TitleFetcher` + codec historique (unit-testable)

**Files:**
- Create `engine/TitleFetcher.kt`, `data/HistoryCodec.kt`, Test `app/src/test/.../engine/TitleParserTest.kt`, `.../data/HistoryCodecTest.kt`

**Interfaces:**
```kotlin
object TitleParser { fun parseTitle(html: String, fallback: String): String } // strip "| Scribd", "Lea en línea", décode entités &amp;&#39;&quot;&lt;&gt;&nbsp;
class TitleFetcher • suspend fun fetch(docUrl: String): String? // GET 5s timeout, UA Mobile Chrome, ne jette jamais (retourne null)
data class HistoryEntry(id: Long, docId: Long, title: String, fileName: String, downloadId: Long, completed: Boolean, sizeBytes: Long, addedAt: Long)
object HistoryCodec { fun encode(entries: List<HistoryEntry>): String ; fun decode(json: String): List<HistoryEntry> } // org.json.JSONArray, propriétés exactes ci-dessus
```
- [ ] **Step 1: tests FAIL** (title html réel incluant entités + cas fallback ; decode/encode bisimulation, champs couverts, tolère proto vide/JSON null)
- [ ] **Step 2:** FAIL (`unresolved reference`)
- [ ] **Step 3:** implémenter
- [ ] **Step 4:** PASS ; commit `feat: title + history codec`

---

### Task 6: `PdfValidator` ( têteport) + `Downloader` basics

**Files:**
- Create `engine/PdfValidator.kt`, `download/Downloader.kt`, Test `app/src/test/.../engine/PdfValidatorTest.kt`, `.../download/FilenameResolverTest.kt`

**Interfaces:**
```kotlin
enum class PdfEligibility { OK, BAD_TYPE, TOO_SMALL }
data class HeadResult(val mime: String?, val contentLength: Long?)
object PdfValidator {
  fun evaluate(mime: String?, contentLength: Long?): PdfEligibility // mime ∈ {pdf, octet-stream, force-download} + Long>2048 | null
  suspend fun head(url: String): HeadResult? // HttpURLConnection HEAD, redirects suivis, 8s timeout, null si réseau KO
}
object FilenameResolver { fun uniquify(want: String, taken: Set<String>): String } // « a.pdf » pris → « a_1.pdf » ; jamais > 120 avant extension
class Downloader(context) {
  fun enqueue(sourceUrl: String, fileName: String, title: String): Long   // Request(uri) mimeType application/pdf, notif HIDDEN, destination partagée par défaut
  fun progress(id: Long): Pair<Long, Long>?  // bytes,total si RUNNING/PAUSED ; null sinon (curseur)
  fun remove(id: Long): Int
}
```
- [ ] **Step 1: tests FAIL** (`evaluate` : pdf→OK, octet 3000→OK, octet 1000→TOO_SMALL, texte→BAD_TYPE, null+pdf→OK ; uniquify : collisions multiples, cap 120, sans extension « proj »→« proj_1 » sans ++.pdf)
- [ ] **Step 2:** FAIL ; **Step 3:** implémenter ; **Step 4:** PASS
- [ ] **Step 5:** commit `feat: validator + downloader`

---

### Task 7: `assets/autopilot.js` — port du `injectAutopilot`

**Files:** Create `app/src/main/assets/autopilot.js` (lu par `readText()` à l'exécution, pas èʸ chaque WebView)

**Interfaces (JS) :** `window.SteVeRBridge.post(jsonString)` — messages uniquement :
`{"event":"status","step":1..4,"key":"init|verify|verify_manual|verify_done|wait|wait_timeout|redirect|analyzing|validating|success"}` ;
`{"event":"captcha"}` (si `.cf-turnstile` visible) ; `{"event":"pdf","url":...}` ; `{"event":"failed","key":"error_detected|error_damaged"}` ; `{"event":"timeout","key":"error_timeout|wait_timeout|captcha_timeout"}`.

- [ ] **Step 1: Port — phases dans le même ordre que `background.js/injectAutopilot` (l.137-332), deltas :**
  1. `updateStatusUI` supprimé → chaque `updateStatusUI(n,title,desc,type)` devient `post({event:"status",step:n,key})`/`{"event":"failed"/…}` selon le cas ; **pas de HUD injecté**.
  2. Garde anti-double injection `window.__spdEngineV1`.
  3. Phase **captcha-devant-formulaire** : si `.cf-turnstile` visible → `post captcha` ; poller 800 ms `[name=cf-turnstile-response]` jusqu'à résolue → **puis re-exécuter la phase formulaire** (chaînage) — jamais d'auto-click sur le challenge.
  4. Garde d'inactivité captcha : 180 s sans résolution → `timeout captcha_timeout`.
  5. Valeur `error_detected` = « #errorContainer, .error-container, [class*="error"] » visible avec texte (`l.207-212`).
  6. Extraction `iframe[src*="viewer.html"]` param `file` (`l.214-239`), `looksPdf` `.pdf|download` ; lien `#btn-download` href ilide → `window.location.href`.
  7. Délais identiques : tick 1000 ms/500 ms/800 ms, 45 essais ilide, 180 essais wait, 800 ms captcha.
- [ ] **Step 2: Revue statique** — comparer chaque phase/sélecteur/délai à `injectAutopilot` ; vérifier la liste des clés émises = table Interfaces.
- [ ] **Step 3:** commit `feat: autopilot.js port`

---

### Task 8: `AutopilotEngine` — WebView + pont

**Files:** Create `engine/EngineEvent.kt`, `engine/AutopilotEngine.kt`
**Interfaces:**
```kotlin
sealed interface EngineEvent {
  data class Status(val step: Int, val key: String): EngineEvent
  object Captcha : EngineEvent
  data class PdfFound(val url: String): EngineEvent
  data class Failed(val key: String): EngineEvent
  data class TimedOut(val key: String): EngineEvent
}
class AutopilotEngine(webView: WebView, scope: CoroutineScope, val events: MutableSharedFlow<EngineEvent>) {
  fun start(pasteUrl: String)   // attache bridge+client, loadUrl vdownloaders ; onPageFinished → évalue autopilot.js (si garde libre)
  fun cancel()                   // stopLoading + about:blank + post {"event":"cancel"} pour stopper les intervals JS
}
```
- WebViewClient : navigation top-level restreinte aux hôtes du flux (`vdownloaders.com`, `ilide.info`) — sortie → raisonnement safe : `cancel()` + `TimedOut("error_detected")` (protection contre les redirections de fermes de liens).
- Script injecté via `evaluateJavascript(assets/autopilot.js)` ; bridge `@JavascriptInterface fun post(msg: String)` → `events.trySend(...)` sur le main dispatcher.
- [ ] **Step 1:** implémenter (détails ci-dessus)
- [ ] **Step 2:** Run: `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 3:** commit `feat: engine webview`

---

### Task 9: `DownloadViewModel` + machine d'états + Live DownloadManager polling

**Files:** Create `ui/DownloadViewModel.kt`, `ui/DownloadUiState.kt` ; Modify `engine/*` si rebranching nécessaire
**Interfaces:**
```kotlin
state: StateFlow<DownloadUiState>
DownloadUiState(
  inputText: String, analyzed: AnalyzedUrl?, phase: Phase, statusKey: String?, 
  progressBytes: Long?, progressTotal: Long?, captchaVisible: Boolean, 
  errorKey: String?, lastPasteUrl: String?, retryable: Boolean, engineRunning: Boolean,
)
enum class Phase { IDLE, ANALYZE, TRANSFER, DOWNLOAD, SAVED, ERROR }
fun onUrlChange(s: String); fun submit(); fun retry(); fun cancel()
```
- Flux : `submit()` → `UrlAnalyzer.analyze()` (null → ERROR invalid_url) → `TitleFetcher` → ÉTAT TRANSFER + `engine.start(pasteUrl)` → consomme le flow d'événements enchaîné : PdfFound → `PdfValidator.head` → non éligible → `errorKey = error_damaged` ; éligible → `Downloader.enqueue` + **`HistoryStore.append(entry completed=false)` immédiat** (pour que le receiver du Task 10 trouve l'entrée même si le process meurt pendant S3) + `Phase.DOWNLOAD` + polling `Downloader.progress(id)` toutes les 500 ms → à la complétion (receiver **ou** polling) → `markCompleted` + `Phase.SAVED` avec l'entrée prête pour « Ouvrir ».
- [ ] **Step 1:** implémenter la séquence exacte ci-dessus ; `keepScreenOn` sera activé par l'UI (Task 11) tant que `engineRunning`.
- [ ] **Step 2:** Run: `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL ; **Step 3:** commit `feat: viewmodel + pipeline`

---

### Task 10: S3 synchronisée + receveur système + historique disque

**Files:** Create `download/DownloadCompleteReceiver.kt` (manifest-registered), Modify `AndroidManifest.xml`, `data/HistoryStore.kt`
**Interfaces:**
```kotlin
class DownloadCompleteReceiver : BroadcastReceiver()  // ACTION_DOWNLOAD_COMPLETE ; appelle HistoryStore.markCompleted(id, size=…)
HistoryStore(context) {
  fun all(): List<HistoryEntry>; fun append(entry: HistoryEntry); fun markCompleted(downloadId: Long, size: Long?)
  fun setFileName(downloadId: Long, fileName: String)  // écrit à l'enqueue pour FILENAME de l'entr'é
  fun deleteEntry(entryId: Long)
}
```
- Manifest : `<receiver DownloadCompleteReceiver android:name=".download.DownloadCompleteReceiver" android:exported="false">` `<intent-filter><action android:name="android.intent.action.DOWNLOAD_COMPLETE"/></intent-filter>`.
- [ ] **Step 1:** implémenter ; **Step 2:** compile ; **Step 3:** commit `feat: system completion receiver`

---

### Task 11: `DownloadScreen` (Compose) — étapes 1-4, progression, carte WebView, erreurs

**Files (Create):** `ui/DownloadScreen.kt`, `ui/components/{StepsHeader.kt, EngineCard.kt, ErrorCard.kt, ProgressRow.kt}` ; Modify `MainActivity.kt` (navigation, bottom bar)
**Interfaces (strings clés, EN+FR) :**
`app_title,input_hint,paste,invalid_url,download_btn,step_analysis,step_fetch,step_download,step_save, step_waiting_text, keep_foreground, engine_title, engine_step, retry, cancel, open, share, delete, delete_missing, library_empty, credit, status{init,verify,verify_manual,verify_done,wait,redirect,analyzing,validating,success}, error{error_detected,error_damaged,error_timeout,captcha_timeout,wait_timeout,network_error,invalid_url}`
- Sémantique clée fixe : `verify_manual`= consigne « complétez la case dans la carte moteur ci-dessous » ; success → bouton « Ouvrir » direct sur l'entrée sauvée.
- Champ URL : `BasicTextField` + bouton « Coller » (ClipboardManager.getText) + validation live (docId preview) ; bouton prism GPL gradient (#A855F7→#EC4899).
- Carte WebView `AndroidView` : `Modifier.fillMaxWidth().height(if (captchaVisible) 380.dp else 180.dp)` ; `keepScreenOn` actif tant que `engineRunning`.
- [ ] **Step 1:** implémenter tous les composants (pas de Preview)
- [ ] **Step 2:** build ; **Step 3:** commit `feat: download screen`

---

### Task 12: `LibraryScreen` + ouvrir/partager/supprimer + FileProvider

**Files:** Create `ui/LibraryScreen.kt` ; Modify `AndroidManifest.xml` (provider), Create `res/xml/file_paths.xml` (`external-path Download/`), `ui/FileActions.kt`
**Interfaces:**
```kotlin
object FileActions {
  fun resolveUri(context, entry: HistoryEntry): Uri?  // MediaStore.Downloads (displayName) → fallback DownloadManager cursor LOCAL_URI (par downloadId) → fallback FileProvider sur File pour API<29
  fun open(context, entry); fun share(context, entry); fun delete(context, entry): DeleteOutcome // DeleteOutcome { DONE, MISSING_FILE } — MISSING_FILE affiché précisément
}
```
- delete (≥29) : `Downloader.remove(downloadId)` en primaire ; si l'enregistrement n'existe plus, repli `ContentResolver.delete` sur l'URI MediaStore résolue ; si aucun n'aboutit → `MISSING_FILE` (jamais de faux succès).
- [ ] **Step 1:** implémenter ; **Step 2:** Run: `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL ; **Step 3:** commit `feat: library`

---

### Task 13: README + CI + build APK locaux + checklist test device

**Files:** Create `README.md` (fr), `.github/workflows/build-apk.yml`, `docs/superpowers/testing/device-checklist.md`

- [ ] **Step 1:** README : prérequis (Studio Ladybug+, JDK17), `assembleDebug` CLI, release_signing (keystore local opt), **emplacement APK** `app/build/outputs/apk/debug/app-debug.apk`, « installer sur téléphone » (adb install / olive fichier), limites honnêtes (concurrence captcha, usage personnel, respect des droits), dépannages.
- [ ] **Step 2:** workflow GH Actions (trigger push/PR, actuel avec checkout/setup-java 17/temurin, gradle 8.11.1, `gradle assembleDebug testDebugUnitTest`, upload artifact `app-debug.apk`).
- [ ] **Step 3:** build locaux avec la toolchain du Task 1 : Run: `.\gradlew.bat :app:testDebugUnitTest` (Expected: PASS) puis `.\gradlew.bat :app:assembleDebug` (Expected: `app\build\outputs\apk\debug\app-debug.apk` présent — c'est **le fichier .app/APK livrable**).
- [ ] **Step 4:** device-checklist.md : sur appareil/émulateur, tester 12 scénarios (lien valide → PDF enregistré, URL invalide → rejet précis, captcha manuel complété, attente timeout, bibliothèque open/share/delete API 29+, delete sur entrée expirée → MISSING_FILE,.kill app pendant S3 → le receiver complète quand même).
- [ ] **Step 5:** commit final `docs: README + CI + device checklist` ; tag `v1.0.0-steVeR`.
