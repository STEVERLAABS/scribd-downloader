# SDD ledger — plan: docs/superpowers/plans/2026-10-22-android-app.md

Pre-flight: interfaces cruzadas vérifiées — T4 AnalyzedUrl↔T9, T5 HistoryEntry↔T9/T10/T12, T6 PdfValidator+Downloader↔T9, T7 bridge-vocab↔T8↔T9, T8 AutopilotEngine(webView,scope,events)↔T9 : signatures cohérentes, aucun conflit bloquant.

Ruling (setup): le repo est créé par ce travail (desktop non-git) ; exécution directement sur sa branch par défaut, pas de worktree — rien d'autre n'existe dans ce dépôt à isoler. Coût si erroné : aucun (repo vierge).
Ruling (script): scripts task-start/task-done indisponibles en shell Windows → ledger tenu manuellement avec les mêmes champs (BASE = dernier commit avant la tâche ; ligne 'Task N: complete' après verification).
Ruling (T10 interface): HistoryStore n'aura PAS de setFileName(downloadId,…) séparé — append(entry) inclut déjà fileName à l'enqueue ; coût si erroné : aucun, une API de moins.
Ruling (T8 interface, révisée): AutopilotEngine ctor = (webView, events) — le paramètre `scope` était inutilisé (les émissions SharedFlow n'en ont pas besoin). Signature pré-flight T8↔T9 ajustée en conséquence; l'écran est propriétaire du WebView + moteur (un seul instance/session), le VM ne fait que collecter `engine.events`.
Ruling (env, T-tests): le run TDD RED local (testDebugUnitTest) est IMPOSSIBLE sur ce poste — aucun JDK ≤23 (Java 25 seul), aucun SDK Android, aucun Gradle, et la connexion ne tient pas le téléchargement de la toolchain (~600 Mo : 0 B/s soutenu, timeouts, dl.google.com/adoptium/gradle tous en échec). Les tests (écrits EN PREMIER) deviennent la phase verte vérifiée par CI (build-apk.yml : testDebugUnitTest + assembleDebug). Coût si erroné : un test peut échouer en CI → fix immédiat dans le même workflow ; le code reste génératif.
Ruling (env, T13/APK): la génération de l'APK sur ce poste est donc impossible aujourd'hui ; 3 voies documentées (README) : Android Studio, CLI, GitHub Actions artefact. Aucun contournement "honnête" n'existe sans toolchain.
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

Revue finale: dispatch subagent code-reviewer sur tout le diff → findings traitée ci-dessous (à compléter après la revue).
