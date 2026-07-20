# Chantier Umihi Music — fork elie00

> Document de passation pour reprendre le travail sur un autre poste.
> Dernière mise à jour : 2026-07-20 (~15h30), branche `agent/finalize-m3-experience`, remote `origin = github.com/elie00/umihi-music`.

## Contexte général

Fork personnel d'`ilianoKokoro/umihi-music` (lecteur YouTube Music, Kotlin + Jetpack Compose, Material 3). Direction assumée : divergence du projet d'origine.

Décisions structurantes déjà actées :
- **Auto-updater upstream désactivé** (`UPDATER_ENABLED=false` pour la flavor `standalone`) : l'app ne propose plus les releases d'ilianoKokoro. Aucun remote git vers l'upstream.
- **APK signé en debug local** (pas de `keystore.properties` dans le repo) → les mises à jour se font en recompilant et installant par-dessus.
- Téléphone cible : **Samsung S24 Ultra** (arm64, Android récent). minSdk 24, targetSdk 37.
- Convention de travail : **micro-commits par fonctionnalité + push immédiat** après chaque vague.

## Vague 1 — ✅ terminée, commitée, poussée, build vert

| Commit | Contenu |
|---|---|
| `a88b0c2` | Lecture confinée : un titre lancé depuis un album/artiste met en file uniquement cette collection (plus de continuation radio hors registre ; radio explicite, play-next, add-to-queue inchangés) |
| `ecb338f` | Vrai plein écran S24 Ultra : `resizeableActivity`, max_aspect 2.4, barres transparentes, cutout `shortEdges` |
| `7dcc3a0` | Audio officiel par défaut : résolution de la contrepartie ATV via watch-next quand le titre est un clip, repli sur l'original |
| (suivant) | Thèmes sélectionnables : Dynamic (Material You) + 6 palettes seed (DataStore `theme-color`, `SeedSchemes.kt`, `ThemeColorDialog.kt`) |
| `dbf08e3` | Recherche croisée : chaque onglet (Titres/Albums/Artistes) exécute sa recherche ciblée pour la même requête, cache par requête |

Corrections annexes commises avant : alias SQL réservé `current` (Room, `d84de0f`), smart-cast action widget (`52539db`).

## Vague 2 — ⏳ en cours (4/5 chantiers passés au moment de la rédaction)

Workflow multi-agents, implémentation séquentielle. **Pas encore committée.**

1. ✅ **Page Bibliothèque** (profil du compte) : titres likés, albums sauvegardés, artistes suivis. Strictement "ma collection" — les nouveautés n'y sont PAS (décision utilisateur). Nouveaux fichiers : `data/datasources/LibraryDataSource.kt`, `data/repositories/LibraryRepository.kt`, `models/Library.kt`, `ui/screens/library/`.
2. ✅ **Navigation 3 onglets** Accueil / Recherche / Bibliothèque + **Accueil recentré** découverte/reprise avec section **"Nouveautés des artistes suivis"** (sur l'Accueil, PAS dans la Bibliothèque).
3. ✅ **Nom d'artiste cliquable dans le lecteur** → page artiste (mini-player conservé).
4. ✅ **Menu ⋮ du lecteur** : bottom sheet Material 3 type YT Music, actions non redondantes avec celles déjà visibles sur le lecteur (`ui/screens/player/components/PlayerMenuBottomSheet.kt`).
5. ⏳ **Pages album enrichies** : type (Album/Single/EP) • année • nb titres • durée totale, description, autres versions.

**À faire à la fin de la vague 2** : micro-commits par fonctionnalité (5 commits) + push, build de contrôle `assembleStandaloneRelease`, corriger les erreurs de compil éventuelles en commits séparés.

## Vague 3 — ⬜ à lancer après commit vague 2

1. **Thème du lecteur adaptatif à la pochette** : base existante `ui/theme/ArtworkPlayerTheme.kt` (commit upstream `4b07eae`) à pousser au bout — schéma M3 complet généré depuis la couleur dominante, dégradé de fond, contrôles/slider teintés, transition animée ~500 ms entre titres, mini-player assorti, repli sur le thème des réglages si pochette absente/terne.
2. **Chargements plus rapides** : audit démarrage (lectures synchrones au splash), Baseline Profile à jour (module `baselineprofile` présent dans le repo), cache stale-while-revalidate des réponses API (accueil, album, artiste, recherche), requêtes indépendantes en parallèle, squelettes de chargement, réglage cache Coil.
3. **Réduction taille APK** : build **arm64-v8a uniquement** pour le téléphone (l'actuel est universel ~5,8 Mo), R8 full mode, audit ressources/langues. Garder éventuellement un APK universel pour partage.

## Vague 4 — ⬜ à lancer après commit vague 3 (workflow demandé explicitement)

Les 16 fonctionnalités validées, regroupées en ~10 chantiers séquentiels :

1. **Paroles synchronisées** (karaoké, source type LRCLIB, repli paroles statiques)
2. **Hors-ligne intelligent** : auto-téléchargement likés/playlists choisies en Wi-Fi, purge auto par quota (s'appuyer sur WorkManager + DownloadHelper existants)
3. **Crossfade réglable + gapless** (ExoPlayer)
4. **Cluster contrôles lecteur** : sleep timer (avec option "fin du titre en cours"), vitesse de lecture, boucle A-B
5. **Reprise de position** contenus longs + bloc "Reprendre l'écoute" en tête d'accueil
6. **File intelligente** : radio depuis tout titre/album/artiste, suggestions de fin de file optionnelles et visuellement séparées de la file utilisateur
7. **Playlists locales éditables** (drag-drop, fusion, tri) + **export/import** (l'import Spotify existe déjà : `core/spotify/`)
8. **Notifications de sorties** : vérification périodique des artistes suivis + notification locale
9. **Android Auto / Wear OS** (arborescence `MediaLibraryService` déjà en place) + **égaliseur mémorisé par sortie audio** (BT vs haut-parleur)
10. **Gestes lecteur** (swipe pochette = changer de titre, double-tap = ±10 s) + **recherche vocale** + recherche dans les likés + **sauvegarde/restauration** locale + **stats enrichies** (Wrapped, top par période, heatmap)

## Pipeline restant (ordre strict, une vague à la fois pour éviter les conflits d'édition)

```
vague 2 (fin) → micro-commits + push → build contrôle
→ vague 3 → micro-commits + push → build contrôle
→ vague 4 → micro-commits + push
→ build final → APK arm64 → livraison
```

## Environnement de build (ce PC de bureau — À REFAIRE sur l'autre PC)

Poste d'entreprise sans admin : AppLocker bloque les `.bat` et les installeurs MSI → tout est en **portable** :

- **JDK 21** : `C:\Users\<user>\.umihi-build\jdk\jdk-21.0.11+10` (zip Temurin extrait)
- **SDK Android** : `C:\Users\<user>\.umihi-build\sdk` (cmdline-tools + `platform-tools`, `platforms;android-37.0` ⚠ nouveau nommage avec `.0`, `build-tools;37.0.0`)
- `local.properties` (non versionné) : `sdk.dir=C:\\...\\.umihi-build\\sdk`
- Gradle 9.6.1 via wrapper. Les `.bat` étant bloqués, lancement direct par le jar :

```powershell
$env:JAVA_HOME = "C:\Users\<user>\.umihi-build\jdk\jdk-21.0.11+10"
$env:ANDROID_HOME = "C:\Users\<user>\.umihi-build\sdk"
& "$env:JAVA_HOME\bin\java.exe" -cp "gradle\wrapper\gradle-wrapper.jar" `
  org.gradle.wrapper.GradleWrapperMain assembleStandaloneRelease --no-daemon
```

Sur un PC personnel sans restrictions : installer JDK 21 + Android Studio (ou SDK) normalement et utiliser `gradlew.bat assembleStandaloneRelease` suffit.

- **Sortie APK** : `app/build/outputs/apk/standalone/release/UmihiMusic.apk`
- Licences SDK : si `sdkmanager --licenses` est bloqué, invoquer la classe `com.android.sdklib.tool.sdkmanager.SdkManagerCli` directement avec `java -cp "<cmdline-tools>\lib\*"`.

## Pièges connus / notes

- `current` est un mot réservé SQLite : ne pas l'utiliser comme alias dans les requêtes Room.
- Le build échoue vite sur la nullabilité Kotlin (`intent?.action` etc.) : compiler après chaque vague.
- Samsung peut mémoriser un ancien ratio d'affichage : après install, vérifier Paramètres > Écran > Applications plein écran si des bandes persistent.
- Play Protect avertit à l'installation (signature debug) : "Installer quand même".
- L'APK debug-signé n'accepte pas les mises à jour signées autrement : toujours recompiler avec la même clé debug du poste, ou partager la keystore debug entre les deux PC (`%USERPROFILE%\.android\debug.keystore`) pour pouvoir installer par-dessus depuis l'un ou l'autre.
