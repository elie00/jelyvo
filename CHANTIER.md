# Chantier Jelyvo — fork elie00

> État courant : [audit et validation du 8 octobre 2026](docs/PROJECT_AUDIT_2026-10-08.md). Les sections datées ci-dessous sont historiques ; elles ne remplacent pas ces vérifications.

> Document de passation pour reprendre le travail sur un autre poste.
> Dernière mise à jour : 2026-11-03, branche `main`, remote `origin = github.com/elie00/umihi-music`.

## État du 3 novembre 2026 au soir, sur le Mac

Les PR #2, #3 et #4 sont fusionnées dans `main`, et la **v2.6.14 est publiée**. La mise à jour depuis l'application a été déroulée jusqu'au bout sur un émulateur : proposée, téléchargée, installée, données conservées.

La PR #4 a été compilée et testée sur le Mac : 802 tests JVM sans échec, 65 tests du serveur, lint sans erreur. L'APK de release a été installé par-dessus la 2.6.13 publique sur deux émulateurs. Toujours faire `git fetch` avant de se fier à cette note.

**Vérifié sur deux émulateurs, en release**

- Écoute à plusieurs avec deux vrais lecteurs : lien d'invitation, QR code, membres, exclusion, contrôle réservé à l'hôte, file partagée dans les deux sens. Écart mesuré entre 0,05 et 0,7 s, signal du serveur à 10,0 s.
- Fondu enchaîné (deux lecteurs pendant la durée réglée) et Automix : sur des fichiers à silences, il sort à 40,4 s au lieu de 59,6 s et entre à 5,0 s au lieu de 0. Saut des silences : un titre de 60 s joué en 42 s.
- Feuilles, réordonner la file, menus près des bords, glissement à gauche, gestes du lecteur, accueil sans compte, grand écran, barre d'état sur photos claires et sombres, traduction réelle et hors ligne, paroles MP3, FLAC et M4A, pochettes, dossiers, « Ouvrir avec », minuterie à durée libre, Replay et son affiche, carte image, messages d'échec.

**Corrigé par ces tests** : la file d'un hôte tronquée quand un invité la modifiait, les durées perdues dans une file reçue, les préréglages de l'égaliseur (Android ajoute un caractère nul à leur nom), l'ombre coupée des menus, les marges de plusieurs écrans.

**L'application est maintenant toujours en français**, quelle que soit la langue du téléphone : voir [`docs/I18N.md`](docs/I18N.md). La langue spéciale cachée est retirée.

**Reste à faire, qui demande quelqu'un** :

1. Écouter à l'oreille sur de vrais téléphones : synchronisation à deux, fondu, Automix.
2. Tester le partage des écoutes avec de vrais comptes ListenBrainz et Last.fm. Seul le refus d'un mauvais jeton a été vérifié.
3. Déployer la page d'invitation en français : `cd worker && npx wrangler login && npm run deploy`. Le code est prêt, le serveur en ligne affiche encore l'anglais.
4. Republier le dépôt public `elie00/jelyvo` avec le script de publication, jamais à la main : il réécrit l'historique. Ce script n'est pas dans ce dépôt ni sur le Mac. Tant que ce n'est pas fait, le code public est en retard sur la 2.6.14, or la GPL impose de fournir le code de ce qu'on distribue.

**Non testable au doigt** : la sélection multiple et le glisser-déposer vers les zones de dépôt, réservés à la souris et au stylet (DeX). Le rafraîchissement le plus rapide : l'émulateur reste à 60 Hz.

**Limites assumées**

- Traduction : service web non officiel de Google, appelé seulement quand on appuie sur le bouton. Cache en mémoire uniquement.
- Replay : version compacte sur l'année en cours, sans choix de période ni plusieurs affiches.
- Paroles intégrées : ni Ogg ni Matroska.
- Feuilles et menus : ni flou ni voile de BitChord derrière les menus, à cause de leur propre fenêtre.
- Les pochettes spéciales gardent leur dégradé.
- Les commits de `95e7798e` à `e63d400c` ne compilent pas pris un par un (`8c1748b1` et `6cfd25c1` rétablissent le build) : à savoir pour un `git bisect`.

**Décisions restantes**

- Calage d'Automix sur le tempo : à décider après une écoute réelle.
- PoToken n'est pas écrit, par choix : il sert à contourner la détection de robots. Le repli actuel marche encore, mais reste le point le plus fragile de la lecture.
- Avenir du dépôt privé `umihi-music`, à garder privé pour l'instant : le dépôt public est `elie00/jelyvo`.

Détail des reprises depuis BitChord : [`docs/BITCHORD_BACKLOG.md`](docs/BITCHORD_BACKLOG.md).

## Reprise depuis BitChord (2026-11-02), publiée en 2.6.13

`feat/bitchord-port-v2` et `codex/finish-existing-platforms` sont fusionnées dans `main`. `feat/bitchord-port`, construite par erreur sur la 2.3.1, est remplacée et ne doit pas être reprise.

- **Fait** : scrobbling ListenBrainz et Last.fm, paroles au mot (lrc.red, BetterLyrics, Unison, LRCLIB), fondu enchaîné à deux lecteurs,
  écoute à plusieurs (`worker/` sur Cloudflare, gratuit avec garde-fous, utilisé par défaut ; `backend/` en Go conservé jusqu'au test sur deux téléphones), toute l'interface (thème Studio, navigation et barres dépolies, lecteur, paroles, accueil, recherche, bibliothèque, détails, réglages, écrans secondaires), Automix sans calage sur le tempo (`automix/`, AGPL-3.0), material3 1.5.0-beta02.
- **Vérifié sur émulateur, APK de release signé, en mise à jour depuis la 2.6.12** : démarrage, recherche, lecture, paroles synchronisées, création d'une partie sur le serveur déployé, synchronisation dans les deux sens avec un second appareil simulé (changement de titre, pause, reprise, saut), fondu enchaîné de 6 s (deux lecteurs audio actifs pendant 6 s), transition avec Automix activé sans plantage.
- **Reste à tester** : sur téléphone réel, à l'oreille ; écoute à plusieurs entre deux vrais appareils ; effet réel de l'analyse d'Automix sur le fondu (l'émulateur montre la transition, pas ce que l'analyse a décidé) ; scrobbling avec de vrais comptes ; écrans secondaires.
- **Suite, au 3 novembre** : décisions à prendre, tests restants et reprises possibles depuis BitChord dans [`docs/BITCHORD_BACKLOG.md`](docs/BITCHORD_BACKLOG.md).
- **Reste à faire** : calage d'Automix sur le tempo (modèles ONNX, moteur natif), jamais porté.
- **Composants alignés sur BitChord** (`ui/components/studio/`, branche `feat/bitchord-components`) : interrupteur, curseur, champ de texte, boîtes de dialogue, contrôle segmenté, boutons d'action. Pages de détail teintées par la pochette et photo d'artiste en fond. Feuilles du bas alignées (`StudioSheet.kt`), sans le flou : elles ont leur propre fenêtre. Menus (`StudioMenu.kt`, sans voile ni flou : ils ont leur propre fenêtre) et boutons alignés. Rangées (`StudioList.kt`, le menu et le glissement gardés), cartes et surfaces teintées alignées. Icônes de la barre d'état choisies selon le haut de la photo d'artiste (`StatusBarIcons.kt`). Volets grand écran, rail et widgets alignés aussi, les pochettes spéciales gardent leur dégradé. Tous les tests sur appareil restent à faire, sur le Mac.
- **Test connu en échec sur le poste Windows** : `WearPlaybackBridgeThreadingTest`, qui cherche un saut de ligne LF alors que le clone Windows est en CRLF. Il passe sur le Mac.
- **Captures sans appareil** : `app/build/outputs/theme-showcase`. Toujours faire `git fetch` avant de se fier à `origin/main` depuis ce clone.

## Contexte général

Fork personnel d'`ilianoKokoro/umihi-music` (lecteur YouTube Music, Kotlin + Jetpack Compose, Material 3). Direction assumée : divergence du projet d'origine.

Décisions structurantes déjà actées :
- **Auto-updater upstream supprimé** (`026e614`) : le champ `UPDATER_ENABLED` n'existe plus, `GithubDatasource` / `GithubRepository` / `UpdateDialog` ont été retirés, ainsi que la permission `REQUEST_INSTALL_PACKAGES` et le `FileProvider`. L'app ne propose plus les releases d'ilianoKokoro. Aucun remote git vers l'upstream.
- **Flavor unique** (`5b7daf5`) : la flavor `store` (F-Droid) a été supprimée, elle ne différait plus de `standalone` que par un suffixe. La dimension `version` est conservée pour ne pas renommer les variantes.
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

## Vague 5 (2026-07-22, workflow) — ✅ trous vague 4 comblés, build vert, poussé

Audit préalable : paroles synchronisées LRCLIB, sleep timer (option « fin du titre ») et vitesse de lecture étaient **déjà faits** (`LyricsPanel.kt`, `SleepTimerBottomSheet.kt`, `SpeedSelectorBottomSheet.kt`). Les 3 vrais manques ont été implémentés :

| Commits | Contenu |
|---|---|
| `7952708`, `219b517` | **Sauvegarde/restauration locale** : `core/backup/BackupManager.kt`, JSON versionné (v1) via SAF — historique, playlists locales, file, prefs DataStore (thème/lecteur/égaliseur/recherches). Exclus volontairement : cookies + data-sync-id (Keystore lié à l'appareil), fichiers audio téléchargés. 2 entrées dans Réglages + confirmation avant restauration |
| `7b1b8b3`, `b40aeaf`, `5d7afc9`, `00a6db2` | **Stats enrichies** : `play_history` avait déjà `playedAt` par écoute → aucune migration Room. Sélecteur Semaine/Mois/Année/Tout (segmented buttons), heatmap Canvas 26 semaines type GitHub (`ListeningHeatmap.kt`), carte Wrapped annuelle en dégradé (`WrappedCard.kt` : total, temps d'écoute, top artiste/titre, jour le plus actif) |
| `ebcd4d2`, `96be95f`, `6a0ee21`, `2cb4c17`, `6a57907`, `be20da8` | **Recherche dans les likés** : SearchBar M3 (loupe + clear) sur l'onglet Titres likés, filtre titre+artiste insensible casse/accents (`containsNormalized`), debounce 250 ms, Play/Shuffle agit sur la liste filtrée ; barre des playlists passée au même filtre normalisé + clear. `LocalPlaylistScreen` volontairement non filtré (conflit avec le drag-drop/persistOrder) |

Build `assembleStandaloneRelease` **vert du premier coup** (8m52, 0 fix), APK ~5,9 Mo, poussé `e4e639a..be20da8`. Restent hors périmètre : **Wear OS** (aucun module `:wear`).

## Vague 6 (2026-07-24, workflow) — ✅ durcissement, 8 commits `460b0e5..064cbdc`, poussés

Passe d'audit sur l'héritage upstream et la sécurité. **Aucune fonctionnalité utilisateur ajoutée** : que du nettoyage, de la sécurité et des tests.

| Commit | Contenu |
|---|---|
| `460b0e5` | i18n : `values-fr-rFR` fusionné dans `values-fr` (fr-CA/fr-BE/fr-CH perdaient 29 strings) + override `fr: fr` dans `crowdin.yml` pour empêcher la régénération du dossier |
| `026e614` | Suppression de l'auto-updater upstream mort : `GithubDatasource`, `GithubRepository`, `UpdateDialog`, `UpdateChannelDialog`, `DatastoreRepository.UpdateChannel`, permission `REQUEST_INSTALL_PACKAGES`, `FileProvider` + `file_paths.xml`, overlay `app/src/store/AndroidManifest.xml` |
| `4803066`, `aa8fc66` | CI : retrait du ping Discord et du workflow contributeurs Crowdin, tous deux dépendants de secrets upstream absents ici |
| `7441616` | Sécurité : DataStore / Room / téléchargements exclus des règles de backup (les deux fichiers XML étaient les templates AS vides) ; `KeystoreCrypto` préfixe `v1:` et renvoie `null` au lieu de rendre le chiffré tel quel |
| `9fc560d` | Import Spotify masqué derrière `SpotifyConstants.IS_CONFIGURED`, client id injectable par `-PspotifyClientId` ou `local.properties` |
| `6a7d4ed` | Schéma Room exporté dans `app/schemas` et commité ; chaîne de migrations gardée par `AppDatabaseMigrationsTest` |
| `064cbdc` | 14 tests de régression sur les parseurs InnerTube (`YoutubeDataExtractorTest`) |

## Vague 7 (2026-07-25, workflow) — ✅ 6 constats de l'audit traités, 7 commits `39ffb12..5b7daf5`, poussés

Constats relevés pendant la vague 6 et instruits ligne par ligne. Ordre d'application 5 → 2 → 1 → 3 → 4 → 6, chaque commit laissant l'arbre compilable et les tests verts.

| Commit | Constat traité |
|---|---|
| `39ffb12` | `extractSearchResults` et `extractSearchContinuationSongs` n'avaient **aucun appelant** (orphelins depuis `ec0806f`). Corps du second identique à `extractArtistSongs`, qui reste utilisé → suppression sans perte |
| `1320122` | `toInt()` non gardé sur `lengthSeconds` : une valeur non numérique faisait perdre **tout le titre** (`ApiResult.Error`) au lieu d'une durée. Passé à `toIntOrNull()`. C'était le dernier parse String→nombre non gardé sous `core/` |
| `1be4a22` | **Bug réel** : les liens `/embed/` et `/v/` ouvraient l'app et ne faisaient rien, alors que l'intent-filter les annonce. Parsing déplacé dans `YoutubeUrlParser.kt` (pur JVM, `okhttp3.HttpUrl`) — c'est la cause du silence : l'ancien code passait par `android.net.Uri`, intestable en JVM. `URL_REGEX` élargi à `m.youtube.com` |
| `bc7d695` | `values-eo-rUY` et `values-lol-rUS` (74 clés d'anglais brut, identiques octet pour octet) supprimés. **Sens inverse du correctif français** — voir Pièges connus |
| `de965e3` | Baseline profiles : documenté pourquoi les règles périmées restent en place (c'est R8 qui les jette au build, pas ProfileInstaller) |
| `8f2a1a8` | **CI cassée depuis `c96021c`** : l'étape « Collect APKs » cherchait `UmihiMusic.apk` alors que Gradle produit `Umihi.apk`, donc elle échouait à chaque push sur `main` |
| `5b7daf5` | Flavor `store` supprimée : elle ne différait plus que par un suffixe **et produisait un APK dégradé** (profil baseline invisible depuis `app/src/standaloneRelease` → `baseline.prof` de 13 Ko contre 17 Ko, aucun startup profile). Dimension `version` conservée |

**Suite de tests : 8 classes, 54 tests, tous verts** (JUnit 4.13.2, JVM uniquement — pas de source set `androidTest`).

## Vague 8 (2026-07-25, reprise Claude/Codex) — ✅ terminée et poussée

Le travail interrompu a été repris en micro-commits. L’état courant compte **21 classes / 127 tests
JVM**, un build debug vert et un build release minifié vert.

- Playlists locales : recherche/tri cohérents avec la lecture, drag désactivé tant que la vue est
  transformée ; playlists distantes également filtrables/triables.
- Gestes : swipe d’un titre vers J’aime ou la file.
- Autoplay : « Pas intéressé », persistance bornée, purge de la file et remise à zéro dans Réglages.
- Qualité : Faible/Normale/Élevée, choix réel du flux Android VR/NewPipe, économiseur automatique
  sur réseau mobile et invalidation cohérente des URL mises en cache.
- Sauvegarde : la description des playlists locales survit désormais à l’export/restauration, avec
  compatibilité des anciens JSON.
- Chromecast : SDK différé après la première image, résolution en URL HTTPS signée, MIME transmis,
  fenêtre de pré-résolution, conservation de la file complète, bascule de la `MediaLibrarySession`,
  retour au téléphone, sélecteur Compose et dégradation visible des fichiers/commandes incompatibles.

Validation locale : APK release de 6,5 Mo installé sur émulateur arm64, aucun crash observé. Le
démarrage release profilé a atteint 245 ms sur une mesure mais reste variable sur l’émulateur
(jusqu’à environ 800 ms) : la cible < 300 ms doit donc être confirmée en série sur le S24 Ultra,
pas déclarée garantie à partir d’un meilleur cas. Le lancement des titres profite du préchauffage
des lignes visibles, du cache de session et du chemin Android VR ; un premier titre non préchauffé
reste dépendant du réseau.

### Ce qui est prouvé vs seulement écrit

Prouvé hors device : les deux chemins du client id Spotify ; le garde-fou de migration Room **dans les deux sens** (rouge si `VERSION` passe à 16 sans `Migration(15,16)`) ; les 3 tests `lengthSeconds` (rouges si le garde est retiré) ; les 11 tests d'URL ; l'absence de `REQUEST_INSTALL_PACKAGES` dans l'APK via `aapt2` ; R8 qui jette les règles mortes (34 avant → 0 après) ; lint identique à **249 erreurs avant et après** l'ensemble des travaux.

**Non vérifié, à valider sur device** : tout le comportement runtime de `KeystoreCrypto` (impossible en JVM — `android.util.Base64` et `AndroidKeyStore` absents, et pas de source set `androidTest`) : le préfixe `v1:`, le `null` et la lecture « déconnecté » sont **compilés, pas exercés**. Prouver les exclusions de backup demande `adb shell bmgr backupnow` puis une restauration sur un second appareil. Et la livraison réelle des intents `/embed/` et `/v/` par l'OS est déduite du manifeste, jamais observée.

## Décisions tranchées pendant les vagues 6-7

| Question | Décision |
|---|---|
| Flavor `store` : garder, supprimer, ou lui redonner un rôle ? | **Supprimée**, dimension `version` conservée. Pas de publication F-Droid prévue ; supprimer les flavors *entièrement* imposerait un `git mv` de 67 550 lignes de profil baseline invérifiable sans build release complet |
| Supprimer `extractSearchContinuationSongs` avec `extractSearchResults` ? | **Oui**, même cause racine, corps identique à `extractArtistSongs` qui reste |
| Ajouter `<data android:pathPrefix="/shorts/" />` au manifeste ? | **Non** : le correctif de parsing fait déjà marcher les Shorts via le partage. Revendiquer les Shorts partout est une décision d'identité produit |
| Élargir `URL_REGEX` à `m.youtube.com` dans le même commit ? | **Oui**, c'est la seconde moitié du même défaut |
| Régénérer les baseline profiles ? | **Non** : exige un device rooté, et la dernière tentative (`055f358`) a été revert par `60dff64` pour perte de couverture réelle |
| Supprimer `crowdin.yml` avec le workflow Crowdin ? | **Non** : le mapping `fr: fr` y est indispensable pour ne pas recréer `values-fr-rFR` |

## Cast / Chromecast — ✅ implémenté, validation physique restante

Le rejet précédent est obsolète. Les obstacles identifiés ont été traités sans serveur HTTP local :

1. `CastResolvingPlayer` remplace les URL `watch?v=` par des URL CDN HTTPS signées avant
   consommation et transmet le MIME réel au Default Media Receiver.
2. La file complète reste visible ; la position courante et deux successeurs sont renouvelés avec
   une marge de validité de 30 minutes. Les ajouts et remplacements de file passent par le même
   wrapper.
3. La `MediaLibrarySession` conserve son contrôleur et change seulement de `Player`, ce qui préserve
   le mini-player, Android Auto, la file, le widget et la reprise lors du retour au téléphone.
4. Les titres MediaStore sont exclus explicitement. Un téléchargement YouTube reste castable par
   résolution réseau ; aucun fichier privé n’est exposé sur le LAN.
5. Le SDK ne démarre qu’après la première image et disparaît proprement des appareils sans Google
   Play Services. Le picker est 100 % Compose/MediaRouter, sans migration AppCompat.

Limite connue : les URL signées peuvent rester sensibles à la topologie IP du réseau et aucun
Chromecast physique n’était disponible pendant cette session. Valider une session longue, le
passage IPv4/IPv6, la prélecture et le retour téléphone sur le réseau réel avant diffusion large.

## Identité Jelyvo — ✅ appliquée

- `1bf8d804` remplace la marque visible, les métadonnées Fastlane, la documentation et le nom de
  sortie par `Jelyvo.apk`.
- `254ba06f` installe le symbole aux deux rubans stéréo corail/prune dans le splash, l’accueil,
  le lecteur, les icônes adaptatives/rondes/monochromes et les visuels Play Store.
- L’application id `ca.ilianokokoro.umihi.music`, le schéma OAuth `umihi://` et les noms de
  stockage internes restent volontairement inchangés : une installation Jelyvo met ainsi à jour
  l’ancienne app sans perdre comptes, playlists, préférences ni téléchargements.
- Validation : 127 tests JVM réussis, APK debug et release minifié construits, debug installé par
  mise à jour sur l’émulateur puis contrôlé en français et en thème sombre.

## Vague 9 (2026-07-27) — ✅ fonctionnalités UI en rafale, puis signature + updater

Une trentaine de micro-commits UI le 27/07 (album Spotify-like, file réordonnable, accueil For You,
réglages étendus, presets de thème dont OLED, animations lecteur/mini-player, widget 2x2, profil
local avec avatar monogramme, suppression de titres de l'historique par appui long, pochettes
d'auto-playlists dérivées du thème actif). Puis le lot distribution :

### Signature pérenne — ✅
- L'APK installé sur le S24 Ultra est signé avec la clé debug du Mac (`~/.android/debug.keystore`),
  empreinte SHA-256 `b47ac9b8…be081d`, certificat valide jusqu'en 2056.
- Cette clé est copiée en `~/keystores/jelyvo-release.keystore` et devient la clé officielle via
  `keystore.properties` (gitignoré, à la racine) : `signingConfigs.release` la ramasse déjà.
  `signingReport` confirme la même empreinte pour `standaloneRelease`.
- ⚠ **Sauvegarder `~/keystores/jelyvo-release.keystore` hors du poste** (sa perte interdit toute
  mise à jour installable). C'est la clé debug standard (mot de passe `android`) : ne jamais la
  committer.

### Auto-updater Jelyvo — ✅ (ressuscité de `026e614`, simplifié)
- Le dépôt `elie00/umihi-music` est **privé** → les releases publiques vivent dans le dépôt dédié
  **public** `elie00/jelyvo-releases` (APK signés + notes uniquement). L'app lit
  `api.github.com/repos/elie00/jelyvo-releases/releases/latest`.
- Réintroduits : `GithubDatasource/Repository`, `GithubReleaseResponse` (sans canal beta),
  `GithubRateLimitException`, `VersionManager` (check au lancement + manuel, download avec
  progression, install via FileProvider, versions ignorées en Room — entité `Version` conservée
  en vague 6 précisément pour ça), `UpdateDialog`, section Réglages « Mises à jour »
  (check manuel + toggle auto, clé DataStore `auto-update` réutilisée), permission
  `REQUEST_INSTALL_PACKAGES` + FileProvider + `file_paths.xml` au manifeste.
- Comparaison de versions extraite en `core/helpers/VersionCompare.kt` pur JVM (segments non
  numériques → 0, `-beta` < stable de mêmes chiffres), couverte par `VersionCompareTest` ;
  parsing DTO couvert par `GithubReleaseResponseTest`.
- **Publication** : `scripts/publish-release.sh` — bumper `appVersionName`, le script build
  `-Pbeta=false`, refuse de publier si l'empreinte de signature diffère de la clé officielle,
  puis `gh release create vX.Y.Z Jelyvo.apk` sur `jelyvo-releases`.
- ⚠ **Amorçage** : les APK déjà installés (2.0.0) n'ont pas l'updater — la première build
  updater-capable s'installe à la main ; les suivantes se proposeront toutes seules.

### CI GitHub Actions — ❌ cassée côté compte, hors périmètre
Tous les runs échouent en ~4 s : « account payments have failed or spending limit needs to be
increased » (dépôt privé = minutes payantes). La release se fait en local via le script ci-dessus ;
réparer la facturation (ou passer le repo en public) rendrait `build-release.yml` de nouveau
pertinent — il faudrait alors aligner son étape « Collect APKs » sur `Jelyvo.apk` (encore `Umihi.apk`).

## Décisions encore ouvertes

1. **Id de langue Crowdin** : le mapping `fr: fr` de `crowdin.yml` utilise l'id standard, **non vérifié** (pas d'accès à `api.crowdin.com`). À confirmer dans Settings > Languages du projet 878334 **avant la prochaine synchro**, sinon `values-fr-rFR` réapparaît.
2. **Espéranto côté serveur Crowdin** : la suppression de `values-eo-rUY` ne tient que si l'espéranto est désactivé côté projet Crowdin. Sinon le dossier sera recréé — et il ne faut **surtout pas** le corriger par un mapping `eo: eo` (voir Pièges connus).
3. **`fallbackToDestructiveMigration(dropAllTables = true)`** : conservé. Le retirer transforme une perte de données silencieuse en crash franc — arbitrage produit, pas technique.
4. **Échecs silencieux des deep links** : les constats 1 et 2 transforment tous deux un échec bruyant en échec discret dans le même flux, sans log ajouté. À reprendre si des utilisateurs signalent des liens « qui ne font rien ».

## Reste à faire

1. **Validation sur device (S24 Ultra + Chromecast)** : installer l’APK release courant et dérouler
   vidéo/PiP, hors-ligne, backup/restore, audio local, qualité/data saver, gestes, « Pas intéressé »,
   recherche/tri de playlists, liens partagés et session Cast longue avec retour téléphone. Mesurer
   au moins 10 cold starts et 20 lancements de titres/vidéos préchauffés pour juger le p95 < 300 ms.
2. **Wear OS** : seul trou restant de la vague 4 (précédent réussi sur suivi-musculation : module `:wear` P1/P2).
3. **Build de contrôle** `assembleStandaloneRelease` après tout nouveau lot. Le build de cette vague
   est vert ; APK : `app/build/outputs/apk/standalone/release/Jelyvo.apk`.

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

- **Sortie APK** : `app/build/outputs/apk/standalone/release/Jelyvo.apk` (la marque visible a été renommée sans changer l'application id historique, afin de préserver les mises à jour et les données locales)
- Licences SDK : si `sdkmanager --licenses` est bloqué, invoquer la classe `com.android.sdklib.tool.sdkmanager.SdkManagerCli` directement avec `java -cp "<cmdline-tools>\lib\*"`.

## Pièges connus / notes

- `current` est un mot réservé SQLite : ne pas l'utiliser comme alias dans les requêtes Room.
- Le build échoue vite sur la nullabilité Kotlin (`intent?.action` etc.) : compiler après chaque vague.
- Samsung peut mémoriser un ancien ratio d'affichage : après install, vérifier Paramètres > Écran > Applications plein écran si des bandes persistent.
- Play Protect avertit à l'installation (signature debug) : "Installer quand même".
- ⚠ **`values-eo` n'est PAS un doublon de `values-eo-rUY` — le sens de la fusion est INVERSE du correctif français.** Pour le français (`460b0e5`), c'est le dossier régionalisé `values-fr-rFR` qui était riche. Pour l'espéranto c'est l'inverse : `values-eo` (130 clés) porte l'easter egg uwu/LOLCAT, `values-eo-rUY` (74 clés) n'était que de l'anglais non traduit. **Ne jamais ajouter `eo: eo` dans `languages_mapping` de `crowdin.yml`** — cela pointerait l'espéranto non traduit de Crowdin sur `values-eo` et écraserait l'easter egg à la prochaine synchro.
- ⚠ **Tâches de test Gradle** : `:app` ayant des flavors, `:app:testDebugUnitTest` **n'existe pas**. Utiliser `:app:testStandaloneDebugUnitTest`.
- ⚠ **Ne jamais éditer à la main** `app/src/standaloneRelease/generated/baselineProfiles/*.txt` (36 915 + 30 635 lignes). Les règles périmées ne coûtent rien : R8 les jette au build, ligne par ligne. Tentative de régénération déjà faite et annulée (`055f358` revert par `60dff64` pour perte de couverture réelle).
- ⚠ **Lint est rouge de base** : 249 erreurs préexistantes (`MissingIntentFilterForMediaSearch` et autres), aucune baseline configurée. `:app:lintStandaloneDebug` **échoue donc toujours** et n'est pas utilisable comme garde-fou tel quel — comparer le nombre avant/après plutôt que d'attendre un vert.
- L'APK debug-signé n'accepte pas les mises à jour signées autrement : toujours recompiler avec la même clé debug du poste, ou partager la keystore debug entre les deux PC (`%USERPROFILE%\.android\debug.keystore`) pour pouvoir installer par-dessus depuis l'un ou l'autre.
