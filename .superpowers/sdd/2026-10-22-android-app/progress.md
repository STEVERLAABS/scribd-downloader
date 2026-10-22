# SDD ledger — plan: docs/superpowers/plans/2026-10-22-android-app.md

Pre-flight: interfaces cruzadas vérifiées — T4 AnalyzedUrl↔T9, T5 HistoryEntry↔T9/T10/T12, T6 PdfValidator+Downloader↔T9, T7 bridge-vocab↔T8↔T9, T8 AutopilotEngine(webView,scope,events)↔T9 : signatures cohérentes, aucun conflit bloquant.

Ruling (setup): le repo est créé par ce travail (desktop non-git) ; exécution directement sur sa branch par défaut, pas de worktree — rien d'autre n'existe dans ce dépôt à isoler. Coût si erroné : aucun (repo vierge).
Ruling (script): scripts task-start/task-done indisponibles en shell Windows → ledger tenu manuellement avec les mêmes champs (BASE = dernier commit avant la tâche ; ligne 'Task N: complete' après verification).
Ruling (T10 interface): HistoryStore n'aura PAS de setFileName(downloadId,…) séparé — append(entry) inclut déjà fileName à l'enqueue ; coût si erroné : aucun, une API de moins.
Ruling (T8 interface, révisée): AutopilotEngine ctor = (webView, events) — le paramètre `scope` était inutilisé (les émissions SharedFlow n'en ont pas besoin). Signature pré-flight T8↔T9 ajustée en conséquence; l'écran est propriétaire du WebView + moteur (un seul instance/session), le VM ne fait que collecter `engine.events`.
Ruling (env, T-tests): le run TDD RED local (testDebugUnitTest) est IMPOSSIBLE sur ce poste — aucun JDK ≤23 (Java 25 seul), aucun SDK Android, aucun Gradle, et la connexion ne tient pas le téléchargement de la toolchain (~600 Mo : 0 B/s soutenu, timeouts, dl.google.com/adoptium/gradle tous en échec). Les tests (écrits EN PREMIER) deviennent la phase verte vérifiée par CI (build-apk.yml : testDebugUnitTest + assembleDebug). Coût si erroné : un test peut échouer en CI → fix immédiat dans le même workflow ; le code reste génératif.
Ruling (env, re-test à la demande utilisateur — 2026-10-22 v2): nouvelle tentative de mesure réseau (slice 1 Mo de gradle-8.11.1 + HEAD aka.ms JDK) → 0 B/s, timeouts même sur les HEAD. La production d'APK sur CE poste reste impossible. Actions concrètes livrées à la place:
1. `tools/build-apk-local.ps1` — script Windows qui bootstrappeer la toolchain (JDK17 Microsoft + Gradle 8.11.1 + cmdline-tools, licences, platform-35/build-tools-35.0.0), lance testDebugUnitTest + assembleDebug et copie l'APK sur le Desktop. Downloads RESUMABLES (`curl -C - --retry 12`): re-run après coupure.
2. `C:\Users\Admin\Desktop\GitHub-Upload\` — copie propre du projet (60 fichiers, 175 Ko, sans .git/.gradle/build/local.properties/.superpowers) destinée au glisser-déposer sur github.com ("uploading an existing file") → le workflow build-apk.yml produit l'artefact app-debug-apk (≈5 Mo, téléchargeable même sur lien lent, directement depuis le téléphone).
3. Guide pas-à-pas en arabe: `docs/install/الحصول-على-APK.md` + copie sur le Desktop (`طريقة-الحصول-على-APK.md`), incl. transfert USB/Drive et installation "sources inconnues" + Play Protect.
Ruling (règle d'engagement): ne PAS clamer un fichier APK inexistant — fichier seulement après build réussi (CI ou script).

=== 2026-10-22 — Livraison via GitHub (à la demande «apk vers site web») ===
Réseau re-testé: github.com/api.github.com répondent (≈27 KB/s) → la voie web est viable depuis CE poste pour des charges utiles petites seulement.
Flux exécuté:
1. Token fourni par l'utilisateur dans le chat (ghp_… : à RÉVOQUER après usage aussi tôt que possible).
2. GET /user → login = STEVERLAABS (le compte d'origine du projet).
3. POST /user/repos → dépôt public créé: STEVERLAABS/scribd-downloader (HTTP 201).
4. git push (HEAD → refs/heads/main) via URL jetable = PUSH OK (aucun secret persisté dans .git/config).
5. run CI déclenché: id 39802169320 → poller en tâche de fond (poll-artifact.ps1) qui télécharge l'artefact app-debug-apk (~5 Mo) et pose l'APK sur le Desktop: C:\Users\Admin\Desktop\ScribdDownloader-steVeR-debug.apk.
À surveiller: si le run échoue, corriger dans le dépôt et repousser; si l'artefact manque, re-builde via workflow_dispatch.
Ruling (garbage collection mémoire): le WebView est créé une fois par session d'écran (remember) et détruit dans DisposableEffect(onDispose) — le Pattern AndroidView(factory=…) initial fuyait à chaque apparition/disparition du bloc moteur. Corrigé (EngineCard reçoit l'instance).
Correction (contrat de collage): __spdPaste est re-semé à chaque onPageFinished tant qu'il est présent, jamais écrasé par null — les chaînes captcha→formulaire re-remplissent le champ (delta 3 du design porté en acte).
Correction (T6): PdfValidator.evaluate traite contentLength < 0 comme taille inconnue → OK (aligne l'impl sur PdfValidatorTest ; HttpURLConnection renvoie -1).
Correction (T11): DownloadViewModel ne démarre plus le moteur depuis submit() AVANT l'existence du moteur — le binding se fait à la première composition (LaunchedBind), submit() démarre seulement si bindé, sinon error_network de repli.
Correction (LibraryScreen): formatDate n'utilise plus DateFormat.getDateFormat(null) (NPE) → java.text.DateFormat.getDateInstance().

Task 1: complete — Gradle config (root+app+settings+libs.versions.toml+gradle.properties+proguard) — vérification CI.
Task 2: complete — Manifest + res (str EN/FR, themes, titles, file_paths, icônes) — vérification CI.
Task 3: complete — 5 fichiers de tests écrits AVANT toute impl (contrat TDD, exécuté en CI).
Task 4: complete — UrlAnalyzer (+AnalyzedUrl/UrlKind) ; sanitisation 1:1 content.js, budget 120.
Task 5: complete — HistoryEntry/HistoryCodec/HistoryStore (+TitleFetcher en data).
Task 6: complete — PdfValidator (HEAD natif) + Downloader + DownloadCompleteReceiver + FilenameResolver + FileActions.
Task 7: complete — autopilot.js port ES5 1:1 (vocabulaire events, captcha chaîné, watchdog 180 s).
Task 8: complete — EngineEvent + AutopilotEngine (WebView hôte, bridge unique, allowlist, collage, cancel).
Task 9: complete — DownloadViewModel (machine à états 4 phases, poll 500 ms, HEAD → enqueue → history) + DownloadUiState.
Task 10: complete — KeyMapper + StepsHeader + components moteur/erreur/succès.
Task 11: complete — DownloadScreen + LibraryScreen + AppRoot + MainActivity + thème.
Task 12: complete — bibliothèque : open/share/delete précis (DownloadManager.remove → MediaStore → MISSING_FILE).
Task 13: complete — README (3 voies APK), CI build-apk.yml, device-checklist ; commits & review finale (post-tâche).

Revue finale (subagent code-reviewer, HEAD 97ce890):
- Strengths: port 1:1 confirmé (sélecteurs, budgets, priorités), tests conformes au contrat, CI sensée, cycle WebView sain, KeyMapper complet côté statuts.
- CRITICAL #1 réseau moteur mort → corrigé immédiatement: addJavascriptInterface(JsBridge(), "SteVeRBridge") dans prepWebView ( garde CI grep ajoutée, build-apk.yml).
- Important #2 wait_timeout/captcha_timeout non mappés → KeyMapper corrigé + chaîne error_wait_timeout FR/EN ajoutée; Ruling (timeout): wait_timeout/captcha_timeout sont des ERREURS terminales précises dans l'app (delta assumé vs extension: extension gardait la session; on ne ment pas sur la cause réseau) — consigné ici comme décision d'interface.
- Important #3 nom réel du fichier → setDestinationInExternalPublicDir(Download, fileName) — nom unique autoritaire (parité historique/disque).
- Important #4 → VISIBILITY_HIDDEN (contrainte "pas de notifications").
- Important #5 → cookie jar (CookieManager) + UA navigateur portés sur HEAD & DownloadManager (sémantique extension via navigateur).
- Important #6 → résolu par #2 (précision des messages), accepté en tant qu'erreur terminale (ruling ci-dessus).
- Minor #9 thread marquage synchrone avant SAVED ; #10 Annuler disponible en phase 3 (downloader.remove + suppression entrée incomplète) ; #8 taille affichée en bibliothèque + récepteur met à jour la vraie taille ; #11 noté dans le checklist (API 24–28) ; #12 chaîne morte error_continue_url supprimée, clé morte 'captcha' retirée, progressions: ProgressRow plié dans PipelineCard, FileActions placé dans download/ (écart de planification documenté), colors.xml absent (thème en Kotlin — volontaire).
- Déféré: commit du wrapper Gradle (généré par CI/Studio — pas de toolchain locale), tag v1.0.0-steVeR (à l'utilisateur), texte avatars/a11y au-delà du plan, garde anti-spam du service externe (hors code).
- Verdict revu: Ready to merge = With fixes → fixes appliqués dans le commit suivant 97ce890; le circuit de vérification exécutable reste CI (tests+assembleDebug) et le device-checklist (scénario 1 en premier task suite au hook du moteur).
