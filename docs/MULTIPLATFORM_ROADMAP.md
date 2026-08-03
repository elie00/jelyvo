# Feuille de route macOS Apple Silicon et Web

## État d'avancement au 30 juillet 2026

- Android stable : `v2.3.3` est publiée avec signature pérenne et mise à jour intégrée.
- Découplage commencé : `SearchResponseParser` et plusieurs règles de lecture, de recommandation et
  de file disposent déjà de tests JVM purs.
- Blocage actuel : l'application reste organisée autour d'un unique module Android et les écrans
  connaissent encore directement Media3, Room ou `Application`.
- Adaptation livrée : navigation latérale, file permanente du lecteur, redimensionnement continu,
  commandes clavier du lecteur, raccourcis de navigation, focus visible et menus de titres
  accessibles à la souris comme au clavier, ainsi que glisser d'un titre vers la file via le
  mini-lecteur ou vers une playlist locale, sont présents sur `main`. Un volet de lecture et de file
  remplace aussi le mini-lecteur dans les fenêtres de bureau assez grandes. À partir de
  1360 × 700 dp, un panneau de bibliothèque expose en permanence les playlists locales comme cibles
  de dépôt ; la pochette courante et les titres de la file permanente sont eux-mêmes déplaçables
  vers ces cibles. Les pages Album et Artiste séparent aussi leur contexte de leur contenu lorsque
  l'espace réellement restant le permet. Leurs listes prennent désormais en charge la sélection
  multiple conventionnelle Ctrl/Commande–Maj et les actions groupées de lecture, file et playlist.
- Découplage livré : `Track`, `ArtistRef`, `AlbumRef` et `TrackSource` ne dépendent d'aucune API
  Android. L'adaptateur `Song ↔ Track` est couvert par des tests contractuels et le classement des
  Daily/Context Mix repose déjà sur ce modèle partagé sans reconstruire les `Song` transportés. Les
  écritures de playlists locales passent également par `Track` et un adaptateur dédié, quel que
  soit leur point d'entrée Android. L'historique local suit la même frontière : les lectures sont
  enregistrées et relues via `Track` grâce à un adaptateur `PlayHistoryEntry ↔ Track` testé, sans
  changement de schéma Room.
- Seam de lecture ouvert : l'interface `PlaybackEngine` (état, file à identité d'emplacement,
  commandes de transport et ajout à la file) vit dans le domaine pur, avec
  `Media3PlaybackEngine` comme premier adapter Android. Le volet permanent et le mini-lecteur
  observent désormais l'état neutre et émettent leurs commandes via l'interface ; le mini-lecteur
  ne possède plus ses propres listeners Media3. Les raccourcis clavier du lecteur passent aussi
  par cette frontière, avec un calcul pur et testé pour les sauts relatifs de ±10 secondes. Le
  widget persiste désormais les métadonnées d'un `Track` neutre, sans importer Media3 ; ses actions
  restent volontairement des commandes Android vers `PlaybackService` afin de fonctionner lorsque
  l'interface de l'application n'est pas lancée.
- Premier seam catalogue livré : la recherche initiale, les continuations et les suggestions de
  `SearchViewModel` passent par `MusicCatalog`. `YoutubeMusicCatalog` adapte le dépôt Android et
  transporte des `Track`, albums et artistes neutres ; l'onglet Bibliothèque reste explicitement
  local et ne traverse jamais ce contrat.
- Les pages album et artiste passent aussi par `MusicCatalog` : `CatalogAlbumPage`,
  `CatalogArtistPage` et le vocabulaire de shelves neutre (`CatalogShelf`) couvrent en-têtes,
  pistes ordonnées et carrousels ; `Track` transporte désormais la position dans la collection et
  le libellé de compteur d'écoutes, ce qui rend l'aller-retour `Song ↔ Track` sans perte pour ces
  écrans. Les endpoints authentifiés reçoivent leurs cookies via un `settingsProvider` explicite.
- Chantier actif : étendre `MusicCatalog` aux playlists distantes, puis à la résolution de flux.
- Aucun module macOS ou Web ne doit être créé avant que ces trois frontières aient un premier
  adapter Android couvert par des tests contractuels.

## Décision recommandée

Jelyvo peut devenir multiplateforme, mais les deux cibles ne doivent pas être développées en
parallèle.

Ordre recommandé :

1. continuer à découpler le cœur Android ;
2. construire une version macOS Apple Silicon ;
3. stabiliser les interfaces partagées avec deux adapters réels, Android et macOS ;
4. créer ensuite un prototype web anonyme ;
5. décider seulement après ce prototype si un backend avec comptes mérite d'être exploité.

La version macOS est une extension réaliste de l'application actuelle. La version web est plutôt un
produit distinct qui réutilise le même cœur métier.

## Faisabilité

| Cible | Faisabilité | Réutilisation estimée | Difficulté principale |
|---|---:|---:|---|
| macOS Apple Silicon | Élevée | 45 à 60 % | Remplacer Media3 et les intégrations Android |
| Web sans connexion | Bonne | 25 à 40 % | Backend requis pour les appels YouTube |
| Web avec compte et hors-ligne | Difficile | 20 à 35 % | Authentification, cookies, sécurité et arrière-plan |

Ces pourcentages sont des estimations architecturales, pas des mesures contractuelles. Une partie des
fichiers considérés comme liés à Android ne le sont que par une annotation Compose ou Room et
deviendront portables après un découpage raisonnable. D'autres, comme `Song`, mélangent réellement le
modèle musical, Room, Media3, `Bitmap` et `Bundle`.

## Pourquoi commencer par macOS

Compose Multiplatform Desktop fonctionne sur la JVM. Il peut produire un `.dmg` autonome contenant
un runtime Java réduit et des ressources spécifiques à `macos-arm64`. Cette solution permet de
continuer à employer les bibliothèques Java utilisées par Jelyvo, notamment NewPipe, au lieu de
réécrire immédiatement toute l'extraction YouTube.

La documentation officielle couvre :

- les distributions `.dmg` et `.pkg` ;
- les ressources propres à Apple Silicon ;
- la signature et la notarisation macOS ;
- le packaging avec `jpackage` et `jlink`.

Référence :
[Compose Multiplatform — distributions natives](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).

Compose Multiplatform Desktop cible la JVM sur macOS, Windows et Linux :
[dépôt officiel Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform).

### Éléments réutilisables

- parseurs YouTube, dont `SearchResponseParser` ;
- extraction et classification des pages musicales ;
- modèles sérialisables après retrait des types Android ;
- recommandations, statistiques et historique ;
- règles de file d'attente album, artiste, playlist et radio ;
- cache réseau et logique de reprise ;
- une partie des écrans et du thème Compose ;
- tests JVM purs ;
- NewPipe, OkHttp et Kotlin Serialization sur le target Desktop JVM.

Room prend en charge Kotlin Multiplatform à partir de sa version 2.7 et fournit un constructeur de
base propre au desktop JVM :
[Room pour Kotlin Multiplatform](https://developer.android.com/kotlin/multiplatform/room).

DataStore Preferences prend également en charge KMP et le desktop JVM :
[DataStore pour Kotlin Multiplatform](https://developer.android.com/kotlin/multiplatform/datastore).

### Éléments à remplacer ou adapter

- `PlaybackService` et Media3 ;
- notifications et contrôles Android ;
- Android Auto et Android Automotive ;
- widgets Glance ;
- WorkManager ;
- permissions Android ;
- téléchargement et stockage propres à Android ;
- conversions `Song` vers `MediaItem` ;
- gestion Android des sorties audio ;
- Picture-in-Picture Android ;
- intents, `Context`, `Application`, `Bundle`, `Bitmap` et `Uri` ;
- certains éléments Navigation 3 et les insets propres au mobile.

## Le module central : PlaybackEngine

Le premier seam multiplateforme doit être la lecture. Les écrans ne doivent plus appeler directement
Media3 ou `PlayerManager`. Ils doivent connaître une petite interface stable :

```kotlin
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>
    val queue: StateFlow<PlaybackQueue>

    suspend fun load(queue: PlaybackQueue, startIndex: Int)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)
}
```

Cette interface doit masquer :

- la résolution et le renouvellement des URL temporaires ;
- le préchargement du titre suivant ;
- les erreurs réseau ;
- la normalisation du volume ;
- le gapless et le crossfade ;
- la synchronisation de la position ;
- les transitions audio/vidéo ;
- la publication des métadonnées système.

Adapters prévus :

- `Media3PlaybackEngine` pour Android ;
- `MacPlaybackEngine` pour macOS ;
- éventuellement `WebPlaybackEngine` pour le navigateur.

Avec deux adapters, le seam devient réel et les tests peuvent employer un `FakePlaybackEngine`.

### Moteur de lecture macOS

Media3 est Android-only. Le moteur macOS devra être validé par un spike avant de figer son
implémentation.

Options à évaluer :

1. moteur compatible JVM empaqueté avec l'application ;
2. petit pont natif vers AVFoundation ;
3. processus auxiliaire natif avec une interface locale minimale.

Le choix doit être fondé sur :

- AAC/M4A, WebM/Opus et vidéo ;
- gapless et crossfade ;
- consommation CPU et mémoire ;
- changement de sortie audio ;
- AirPlay ;
- licence et redistribution ;
- signature et notarisation ;
- intégration aux contrôles multimédias macOS.

`AVPlayer` sait lire des médias distants et `AVQueuePlayer` gère une file :
[documentation AVPlayer](https://developer.apple.com/documentation/avfoundation/avplayer).

`MPNowPlayingInfoCenter` publie le titre, l'artiste, la pochette et l'état de lecture dans les
contrôles système :
[documentation MPNowPlayingInfoCenter](https://developer.apple.com/documentation/mediaplayer/mpnowplayinginfocenter).

Il ne faut cependant pas décider que le desktop JVM appellera directement AVFoundation avant
d'avoir validé la technique d'interopération et le packaging.

## Découpage du cœur partagé

Structure cible possible :

```text
Jelyvo
├── shared-domain
│   ├── modèles musicaux purs
│   ├── file d'attente
│   ├── recommandations
│   ├── historique et statistiques
│   └── contrats d'erreurs
├── shared-youtube
│   ├── client de catalogue
│   ├── parseurs de réponses
│   ├── résolution de flux
│   └── cache
├── shared-data
│   ├── interfaces de repositories
│   ├── Room KMP
│   ├── DataStore KMP
│   └── import/export
├── shared-ui
│   ├── thème
│   ├── éléments de listes
│   ├── pages Album et Artiste
│   ├── Recherche
│   └── Bibliothèque
├── androidApp
│   ├── Media3PlaybackEngine
│   ├── WorkManager
│   ├── notifications et widgets
│   └── Android Auto
├── desktopApp
│   ├── MacPlaybackEngine
│   ├── contrôles système macOS
│   ├── menus et raccourcis clavier
│   └── packaging DMG
└── webApp
    ├── interface responsive
    ├── WebPlaybackEngine
    └── client du backend Jelyvo
```

Il n'est pas nécessaire de créer tous ces modules Gradle immédiatement. Le découpage doit suivre les
seams réellement utilisés et éviter des modules superficiels qui ne feraient que transmettre des
appels.

## Nettoyage préalable des modèles

Les modèles de domaine ne doivent contenir ni types Android, ni logique de persistance, ni logique
de rendu.

Exemple cible :

```kotlin
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: ArtistRef,
    val album: AlbumRef?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val explicit: Boolean,
    val source: TrackSource,
)
```

Conversions propres aux plateformes :

```text
Track ── AndroidTrackAdapter ──> MediaItem
Track ── RoomTrackMapper ──────> SongEntity
Track ── MacTrackAdapter ──────> MacPlaybackItem
Track ── WebTrackAdapter ──────> MediaMetadata
```

`Song` peut rester temporairement comme modèle Android pendant que `Track` devient le modèle partagé.
Une migration progressive limite le risque de casser l'application existante.

Premier jalon livré sur `main` : le modèle partagé et l'adaptateur Android existent, et le moteur de
recommandations transporte une valeur de plateforme opaque autour de `Track`. Les décisions de
classement n'accèdent donc plus à `Song`, tandis que les écrans et caches Android continuent de
recevoir exactement leurs objets existants. Le second jalon raccorde les créations et ajouts de
playlists locales à `Track` ; Room conserve temporairement son JSON historique grâce à l'adaptateur
`LocalPlaylistSong ↔ Track`, sans migration de base de données.

## Interfaces partagées à introduire

### MusicCatalog

```kotlin
interface MusicCatalog {
    suspend fun search(query: String, filter: SearchFilter): SearchPage
    suspend fun album(id: String): AlbumPage
    suspend fun artist(id: String): ArtistPage
    suspend fun playlist(id: String): PlaylistPage
    suspend fun resolve(track: Track, mode: PlaybackMode): ResolvedStream
}
```

L'interface cache le format InnerTube, les continuations, les corrections orthographiques, le cache
et les stratégies de repli.

### LocalStore

```kotlin
interface LocalStore {
    fun likedTracks(): Flow<List<Track>>
    fun history(): Flow<List<HistoryEntry>>
    fun playlists(): Flow<List<LocalPlaylist>>
    suspend fun saveHistory(entry: HistoryEntry)
    suspend fun savePlaylist(playlist: LocalPlaylist)
}
```

Adapters :

- Room Android ;
- Room Desktop ;
- stockage web ou backend.

### SessionStore

Cette interface doit séparer les préférences ordinaires des données sensibles :

```kotlin
interface SessionStore {
    suspend fun currentSession(): AccountSession?
    suspend fun saveSession(session: AccountSession)
    suspend fun clear()
}
```

Sur macOS, les secrets devront être stockés dans le Trousseau, pas dans un fichier de préférences.

### DownloadScheduler

```kotlin
interface DownloadScheduler {
    fun observe(): Flow<List<DownloadState>>
    suspend fun enqueue(request: DownloadRequest)
    suspend fun cancel(id: String)
}
```

WorkManager devient un détail de l'adapter Android. macOS utilisera sa propre file persistante.

## MVP macOS

### Inclus

- fenêtre adaptative et plein écran ;
- Accueil ;
- Recherche avec tous les filtres ;
- pages Album, Artiste et Playlist ;
- lecture audio officielle ;
- file d'attente contenue dans son contexte ;
- mini-player et lecteur complet ;
- thèmes et couleurs de pochette ;
- historique ;
- bibliothèque locale ;
- raccourcis clavier ;
- touches multimédias ;
- informations Now Playing macOS ;
- cache réseau et pochette ;
- `.dmg` Apple Silicon signé.

### Reporté après le MVP

- connexion au compte YouTube ;
- téléchargements hors-ligne ;
- égaliseur ;
- crossfade avancé ;
- vidéo plein écran ;
- AirPlay ;
- import Spotify ;
- notifications de sorties ;
- mise à jour automatique ;
- App Store.

Cette limitation produit rapidement un lecteur vérifiable avant de déplacer les fonctions les plus
risquées.

## Estimations macOS

Pour une personne à temps plein connaissant le projet :

| Phase | Estimation |
|---|---:|
| Extraction du cœur et interfaces | 2 à 4 semaines |
| Spike du moteur macOS | 1 à 2 semaines |
| MVP recherche, navigation et lecture | 6 à 10 semaines |
| Intégration macOS et packaging signé | 2 à 4 semaines |
| Parité fonctionnelle importante avec Android | 4 à 6 mois |

Ces durées n'incluent pas une éventuelle réécriture complète du moteur de résolution YouTube.

## Pourquoi le web est plus difficile

Le problème principal n'est pas Compose. Une page web ne peut pas reprendre librement les requêtes
actuelles vers YouTube Music.

Les navigateurs appliquent la same-origin policy et CORS aux appels `fetch`. Les requêtes avec
cookies inter-domaines exigent l'accord explicite du serveur, et les politiques de cookies tiers
peuvent encore les bloquer :
[documentation CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS).

Conséquences :

- impossible de transposer directement les appels OkHttp du client Android ;
- impossible de demander au navigateur d'envoyer librement les cookies YouTube ;
- impossible d'exposer des cookies de compte ou des secrets dans le code web ;
- les URL de flux temporaires doivent être renouvelées côté serveur ou via une stratégie sûre ;
- un backend est requis pour une application fiable.

Compose Multiplatform Web/Wasm permet de partager l'interface mais reste en bêta, alors que les
cibles Android, iOS et desktop sont stables :
[FAQ Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/faq.html).

## Architecture web recommandée

```text
Navigateur
├── interface responsive
├── HTMLMediaElement
├── Media Session
├── cache local limité
└── client HTTPS
        │
        ▼
Backend Jelyvo
├── catalogue et recherche
├── parseurs partagés
├── résolution de flux
├── cache à durée courte
├── limitation des requêtes
├── sessions Jelyvo
└── observabilité
```

Le navigateur ne doit jamais recevoir :

- les cookies YouTube bruts d'un autre utilisateur ;
- des secrets d'application ;
- des jetons permanents ;
- des détails permettant de contourner les contrôles du backend.

Le backend doit prévoir :

- chiffrement des secrets ;
- expiration et révocation des sessions ;
- quotas par utilisateur et par IP ;
- prévention du SSRF ;
- validation des URL de média ;
- journaux sans cookies ;
- cache court et borné ;
- protection contre les abus ;
- politique claire de suppression des données.

## Lecture web

Le `WebPlaybackEngine` utiliserait un élément `<audio>` ou `<video>` et exposerait le même état que
les adapters Android et macOS.

La Media Session API permet de publier les métadonnées et de recevoir certaines commandes système,
mais sa disponibilité n'est pas uniforme :
[Media Session API](https://developer.mozilla.org/en-US/docs/Web/API/Media_Session_API).

Limitations probables :

- lecture mobile en arrière-plan moins fiable qu'une application native ;
- crossfade et gapless variables selon le navigateur ;
- stockage hors-ligne limité par les quotas du navigateur ;
- égaliseur et routage audio limités ;
- comportement différent entre Safari, Chrome et Firefox ;
- suppression possible du cache par le système ;
- installation PWA et contrôles système non uniformes.

## MVP web recommandé

Commencer sans compte :

- recherche ;
- résultats Titres, Albums, Artistes et Vidéos ;
- pages Album et Artiste ;
- lecture audio ;
- file d'attente en mémoire ;
- thèmes ;
- historique local ;
- Media Session quand elle est disponible.

Ne pas inclure initialement :

- cookies YouTube ;
- synchronisation de la bibliothèque ;
- téléchargements permanents ;
- import Spotify ;
- notifications ;
- recommandations personnalisées ;
- lecture mobile garantie en arrière-plan.

Ce MVP permet de valider la viabilité technique et les coûts du backend sans stocker de données
sensibles.

## Choix de l'interface web

Deux possibilités :

### Compose Multiplatform Web

Avantages :

- davantage de Kotlin partagé ;
- apparence proche des applications natives ;
- modèles d'état et composables partagés.

Inconvénients :

- target Wasm encore en bêta ;
- adaptation web et intégration DOM à vérifier ;
- taille initiale et temps de chargement à mesurer ;
- une partie de l'interface Android actuelle reste trop couplée à Android.

### Interface DOM dédiée

React, Next.js ou une autre interface DOM peut consommer le backend Jelyvo.

Avantages :

- comportement web conventionnel ;
- accessibilité et navigation navigateur plus naturelles ;
- chargement progressif et outils web matures ;
- séparation nette entre frontend et backend.

Inconvénients :

- interface largement réécrite ;
- moins de code Kotlin partagé ;
- deux systèmes de design à maintenir.

Recommandation : employer Compose Desktop pour macOS, mais choisir l'interface web seulement après un
prototype comparatif. Le backend et les modèles de domaine doivent être indépendants de ce choix.

## Ordre précis du chantier

### Phase 0 — Continuer le découpage Android

1. terminer l'extraction des parseurs hors de `YoutubeDataExtractor` ;
2. rendre les modèles musicaux indépendants de Media3 et Room ;
3. introduire `PlaybackEngine` devant `PlayerManager` ;
4. introduire `MusicCatalog` devant les appels YouTube ;
5. rendre Room et DataStore instanciables via des factories ;
6. maintenir les tests Android verts à chaque micro-commit.

### Phase 1 — Squelette multiplateforme

1. ajouter un module KMP expérimental sans déplacer toute l'application ;
2. y déplacer un premier module pur, par exemple la file d'attente ;
3. compiler Android et desktop ;
4. ajouter les tests `commonTest` et `jvmTest` ;
5. ne déplacer un second domaine qu'après validation du premier.

### Phase 2 — Prototype macOS

1. créer `desktopApp` ;
2. afficher Recherche avec de vraies données ;
3. valider NewPipe et la résolution des flux sur macOS ;
4. comparer les moteurs de lecture ;
5. implémenter `MacPlaybackEngine` ;
6. lire une file d'album entière ;
7. produire un `.dmg` local.

### Phase 3 — MVP macOS

1. porter les pages essentielles ;
2. ajouter Room et DataStore desktop ;
3. intégrer les touches multimédias et Now Playing ;
4. ajouter les raccourcis clavier et menus ;
5. signer et notariser ;
6. tester sur plusieurs Mac Apple Silicon.

### Phase 4 — Prototype web

1. déployer un backend sans compte ;
2. exposer recherche, album, artiste et résolution de flux ;
3. créer un lecteur web minimal ;
4. mesurer CORS, expiration des flux et consommation réseau ;
5. comparer Compose/Wasm à une interface DOM ;
6. décider si le produit mérite une phase de compte utilisateur.

## Critères de validation macOS

- démarrage froid acceptable sur un Mac Apple Silicon ;
- recherche complète dans les quatre catégories ;
- lecture d'un album sans sortir de l'album ;
- préchargement du titre suivant ;
- reprise après pause et sortie de veille ;
- touches clavier et casque fonctionnelles ;
- métadonnées visibles dans le Centre de contrôle ;
- consommation CPU raisonnable pendant la lecture audio ;
- bibliothèque et historique persistants ;
- aucune donnée sensible stockée en clair ;
- `.dmg` signé, notarisé et installable sans avertissement ;
- mise à jour de l'application testée.

## Critères de validation web

- aucune clé ni cookie sensible dans le bundle client ;
- backend protégé contre les URL arbitraires ;
- recherche et lecture fonctionnelles sur Safari et Chrome ;
- comportement documenté sur Firefox ;
- lecture contrôlable via Media Session lorsqu'elle est disponible ;
- expiration des URL de flux gérée sans interruption excessive ;
- cache borné ;
- page utilisable au clavier et avec lecteur d'écran ;
- métriques de latence et d'erreur disponibles ;
- coût d'hébergement mesuré avant ajout des comptes.

## Risques

### Techniques

- dépendance à une interface YouTube Music non officielle ;
- changement régulier des réponses InnerTube ;
- compatibilité des codecs sur macOS ;
- expiration des URL audio ;
- différences de cycle de vie Android, desktop et web ;
- bibliothèque Android actuelle très imbriquée dans les modèles.

### Produit

- attentes différentes entre mobile, desktop et navigateur ;
- besoin de raccourcis, menus et multi-fenêtres sur desktop ;
- comportement web moins fiable en arrière-plan ;
- complexité supplémentaire du support et des releases.

### Exploitation

- signature et notarisation Apple ;
- hébergement et supervision du backend web ;
- protection des sessions ;
- limites réseau et coûts de bande passante ;
- mises à jour coordonnées entre backend et frontend.

## Conclusion

Le meilleur investissement est **Jelyvo macOS Apple Silicon en premier**, avec :

- Compose Multiplatform Desktop sur JVM ;
- un cœur métier progressivement partagé ;
- un `PlaybackEngine` indépendant de Media3 ;
- un adapter macOS validé par un spike ;
- un `.dmg` signé et notarisé.

La version web doit venir ensuite, avec un backend et un MVP sans compte. Elle ne doit pas être
considérée comme une simple cible supplémentaire du build Android.

Le découpage déjà commencé avec `SearchResponseParser` va dans la bonne direction : il concentre une
grammaire complexe derrière une petite interface et prépare la réutilisation du cœur sans déplacer
prématurément toute l'application.
