# Chantier Umihi Music — fork elie00

> Document de passation pour reprendre le travail sur un autre poste.
> Dernière mise à jour : 2026-07-22, branche `main` propre à `dbd7d8b`, remote `origin = github.com/elie00/umihi-music`.

## Contexte général

Fork personnel d'`ilianoKokoro/umihi-music` (lecteur YouTube Music, Kotlin + Jetpack Compose, Material 3). Direction assumée : divergence du projet d'origine.

Décisions structurantes déjà actées :
- **Auto-updater upstream désactivé** (`UPDATER_ENABLED=false` pour la flavor `standalone`) : l'app ne propose plus les releases d'ilianoKokoro. Aucun remote git vers l'upstream.
- **APK signé en debug local** (pas de `keystore.properties` dans le repo) → les mises à jour se font en recompilant et installant par-dessus.
- Téléphone cible : **Samsung S24 Ultra** (arm64, Android récent). minSdk 24, targetSdk 37.
- Convention de travail : **micro-commits par fonctionnalité + push immédiat** après chaque vague.

## État : vagues 1 à 4 ✅ terminées, commitées, poussées sur `main`

### Vague 1 (détail dans l'historique)
Lecture confinée album/artiste (`a88b0c2`), vrai plein écran S24 Ultra (`ecb338f`), audio officiel par défaut (`7dcc3a0`), thèmes sélectionnables (`e68dfb1`), recherche croisée par onglet (`dbf08e3`).

### Vague 2 — ✅
| Commit | Contenu |
|---|---|
| `a78efa5` | Page Bibliothèque du compte (likés / albums sauvegardés / artistes suivis) |
| `ed9b51d` | Navigation 3 onglets + Accueil recentré découverte (« Nouveautés des artistes suivis ») |
| `a2bb1a9` | Nom d'artiste cliquable dans le lecteur → page artiste |
| `7beadbb` | Menu ⋮ du lecteur (bottom sheet M3) |
| `8f46d25` | Pages album enrichies (type • année • nb titres • durée, métadonnées YT Music) |

### Vague 3 — ✅
| Commit | Contenu |
|---|---|
| `e96d9e8` | Thème du lecteur adaptatif à la pochette (schéma M3 complet + transitions) |
| `a6c84e6` | Cache des réponses API + réduction du travail bloquant au démarrage |
| `5fc870e` | Ciblage ABI (arm64) + ressources packagées réduites |

### Vague 4 — ✅ (le gros des 16 fonctionnalités)
| Commit | Contenu |
|---|---|
| `a9b7fdd` | Crossfade réglable sur moteur gapless |
| `4a60f58` | Boucle A-B dans le menu lecteur |
| `01bed7b` | Reprise de position + rangée « Continuer l'écoute » sur l'Accueil |
| `c735389` | Lecture des fichiers audio locaux du téléphone (MediaStore) |
| `9df5f7f` | Playlists locales éditables + export/import JSON |
| `47151f6` | Notifications de sorties des artistes suivis (WorkManager) |
| `94a2f24` | Gestes sur la pochette + recherche vocale |
| `a5545c2` | Hors-ligne intelligent : auto-téléchargement des likés + purge par quota |
| `5a934bd` | Suggestions autoplay marquées et séparées de la file utilisateur |
| `04a84a1` | Radio/autoplay seedée RDAMVM (suggestions dans le sujet) |
| `3cea041` | Égaliseur : presets mémorisés par sortie audio |
| `96cae14` | i18n : traductions FR complètes + strings externalisées |

## Vagues suivantes (hors plan initial) — ✅ aussi sur `main`

- **Recherche enrichie** : onglet Vidéos (`c834b1d`), recherches récentes supprimables (`1d01aec`), carte « top result » (`b62edea`), corrections orthographiques (`b9902ca`), moods/genres depuis l'écran vide (`db42490`).
- **Vidéo** : surface vidéo avec bascule Titre|Vidéo (`eb3f6e2`), plein écran paysage + PiP (`faf2386`), lignes vidéo différenciées façon YT Music (`d0a00ae`), pochette officielle conservée par défaut + action « voir le clip » (`032b4bc`).
- **UI/M3 expressive** : fond flou dérivant depuis la pochette (`03817b9`), contrôles lecteur redessinés (`b4305cf`), mini-player docké avec auto-hide au scroll (`1f65244`), filter chips Spotify dans la Bibliothèque (`02b6189`), like sur chaque menu de titre (`4c9ac53`), provenance de la file affichée (`9a640b2`).
- **Passe perfs du 21/07** : cache accueil persisté sur disque + concurrence OkHttp (`55f41a4`), stabilisation des states / recyclage des listes hétérogènes (`7af360b`), init version hors main thread (`e6dc34b`).
- **Divers** : test normalisation loudness (`6655a49`), Bibliothèque hors connexion : audio local + playlists locales visibles déconnecté (`dbd7d8b`).

## Reste à faire

1. **Validation sur device (S24 Ultra)** : rien de la vague 4 ni des vagues UI/vidéo/perfs n'a été vérifié sur téléphone — recompiler l'APK, installer par-dessus, dérouler : vidéo/PiP, hors-ligne + purge, notifications de sorties, gestes + recherche vocale, égaliseur par sortie, crossfade, thème pochette, perfs de démarrage.
2. **Trous restants du plan vague 4** (à vérifier dans le code avant d'attaquer) : paroles synchronisées LRCLIB (seul `098d800` — message localisé — apparaît), sleep timer + vitesse de lecture, sauvegarde/restauration locale, stats enrichies (Wrapped/heatmap), Wear OS, recherche dans les likés.
3. **Build de contrôle** `assembleStandaloneRelease` après tout nouveau lot + APK arm64 pour livraison.

## Environnement de build (PC de bureau — À REFAIRE sur l'autre PC)

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
