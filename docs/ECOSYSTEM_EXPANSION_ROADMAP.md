# Feuille de route d'expansion de l'écosystème Jelyvo

## État d'avancement au 30 juillet 2026

La phase A est lancée sur la base Android `v2.3.3`. Les premiers incréments sont livrés sur `main` :

- classification réactive compacte, moyenne et étendue ;
- rail latéral à partir de 600 dp ;
- file permanente dans le lecteur lorsque largeur et hauteur le permettent ;
- conservation du lecteur pendant le redimensionnement d'une fenêtre DeX ;
- commandes clavier du lecteur : espace, gauche, droite et Échap ;
- raccourcis globaux vers Accueil, Recherche, Bibliothèque et Réglages ;
- menus des titres accessibles au clic droit, sans modifier les gestes tactiles existants ;
- focus visible au clavier sur la navigation, les titres, la file et les cartes d'accueil ;
- activation des titres avec Entrée et ouverture de leurs menus avec Menu ou Maj+F10 ;
- glisser d'un titre depuis une liste ou une carte d'accueil vers le mini-lecteur pour l'ajouter à la
  file, sans remplacer l'appui long tactile ;
- dépôt d'un titre sur une carte de playlist locale, avec surbrillance de la cible et refus des
  doublons ;
- volet permanent de lecture hors lecteur complet à partir de 1000 × 600 dp : pochette, progression,
  contrôles, file à suivre et cible de dépôt, avec retour automatique au mini-lecteur sous ce seuil ;
  le titre courant et chaque titre à suivre peuvent aussi être glissés vers une playlist locale.
- panneau permanent de bibliothèque à partir de 1360 × 700 dp : accès aux playlists locales,
  pochettes mosaïques, nombre de titres et dépôt direct depuis les écrans de navigation, avec refus
  visible des doublons.

Les prochains lots grand écran concernent les panneaux de contexte album/artiste et la sélection
multiple à la souris.

Les phases desktop, synchronisation et nouvelles plateformes restent volontairement en attente de
l'extraction du cœur. Cet ordre évite de dupliquer Media3, Room et les modèles Android dans chaque
nouvelle application.

## Vision

Jelyvo peut évoluer d'une application Android vers un écosystème musical cohérent sur téléphone,
grand écran, ordinateur, montre et navigateur.

La bonne stratégie n'est pas de reproduire l'application Android sur chaque plateforme. Il faut
partager un même cœur musical, puis construire une expérience adaptée aux capacités et aux usages de
chaque appareil.

```text
                         Jelyvo Sync
                              |
        +----------+----------+----------+----------+
        |          |                     |          |
     Android    Desktop               Web/PWA      TV
        |      macOS/Windows/Linux       |          |
      Wear OS          |             Remote      Cast
                       |
                  iOS/iPadOS
```

Les fonctions déjà présentes dans le projet — Media3, Cast, lecture hors-ligne, statistiques,
sauvegarde, historique et contrôles système — rendent plusieurs extensions réalistes.

## Résumé des possibilités

| Projet | Faisabilité | Valeur produit | Difficulté |
|---|---:|---:|---:|
| Interface tablette et Samsung DeX | Très élevée | Très forte | Faible à moyenne |
| Android TV / Google TV | Élevée | Forte | Moyenne |
| Windows et Linux après macOS | Très élevée | Forte | Faible à moyenne |
| Wear OS compagnon | Élevée | Moyenne | Moyenne |
| Synchronisation entre appareils | Élevée | Très forte | Moyenne à forte |
| Télécommande web locale | Très élevée | Forte | Faible |
| Version web complète | Moyenne | Forte | Forte |
| iPhone et iPad | Moyenne | Très forte | Forte |
| CarPlay | Possible après iOS | Forte | Forte |

Les niveaux de faisabilité décrivent l'architecture actuelle du projet. Ils ne constituent pas des
estimations contractuelles.

## 1. Interface tablette et Samsung DeX

Il s'agit probablement du meilleur prochain investissement visible. Il améliore l'application
Android immédiatement tout en préparant les interfaces desktop.

### Expérience cible

- interface à deux ou trois panneaux ;
- navigation latérale sur grand écran ;
- file d'attente visible en permanence ;
- lecteur vidéo large ;
- bibliothèque et contenu affichés simultanément ;
- glisser-déposer dans les playlists et la file ;
- raccourcis clavier ;
- interactions à la souris et menus contextuels ;
- redimensionnement continu de la fenêtre ;
- mini-player proche d'un logiciel desktop ;
- adaptation automatique entre téléphone, tablette, pliable et DeX.

### Architecture

Les écrans doivent choisir leur structure à partir de l'espace réellement disponible, et non du nom
ou de l'orientation de l'appareil.

Trois classes de disposition suffisent :

- **compacte** : navigation inférieure et un seul panneau ;
- **moyenne** : rail de navigation et panneau de contenu ;
- **étendue** : navigation latérale, contenu et file/lecteur secondaire.

Cette étape permet de valider les écrans adaptatifs avant leur réutilisation sur macOS, Windows et
Linux.

## 2. Android TV et Google TV

Jelyvo possède déjà une base Media3 et Chromecast. Une vraie application TV reste néanmoins un
produit distinct : une interface mobile agrandie ne répond pas aux contraintes d'une télévision.

### Fonctions

- navigation directionnelle à la télécommande ;
- recherche vocale ;
- accueil simplifié ;
- grandes cartes lisibles à distance ;
- lecteur vidéo plein écran ;
- file d'attente latérale ;
- paroles synchronisées en plein écran ;
- mode pochette et informations « maintenant en lecture » ;
- économiseur d'écran musical ;
- playlists téléchargées ou favorites ;
- continuité avec le téléphone ;
- mode soirée avec file collaborative.

### Contraintes

- chaque élément doit posséder un focus visible ;
- aucune action importante ne doit dépendre d'un geste tactile ;
- les écrans doivent limiter la densité de texte ;
- la lecture doit rester contrôlable depuis la télécommande et le téléphone ;
- les parcours de connexion doivent être réalisables avec un QR code ou un code court.

## 3. Windows et Linux

Après la réalisation du socle macOS avec Compose Desktop, Windows et Linux deviennent des cibles
naturelles. Le cœur, une grande partie des écrans et le moteur desktop peuvent être partagés.

### Distributions possibles

- Windows : installateur `.exe` ou `.msi` ;
- Linux : AppImage, Flatpak ou paquet Debian ;
- macOS : `.dmg` ou `.pkg` signé et notarisé.

### Intégrations desktop

- touches multimédias ;
- commandes clavier ;
- icône de zone de notification ;
- menus natifs ;
- glisser-déposer ;
- sélection de la sortie audio ;
- lancement facultatif au démarrage ;
- protocole de liens `jelyvo://` ;
- mise à jour automatique signée ;
- plusieurs tailles de fenêtre ;
- mode mini-player flottant.

Le chantier desktop doit être conçu comme une famille de plateformes, même si macOS Apple Silicon
reste la première cible livrée.

## 4. Jelyvo Sync

La synchronisation représente probablement la fonctionnalité produit la plus importante à moyen
terme.

### Données synchronisables

- playlists locales ;
- titres aimés localement ;
- artistes suivis ;
- historique ;
- positions de lecture ;
- file d'attente ;
- réglages non sensibles ;
- demandes de téléchargement ;
- statistiques ;
- exclusions et préférences de recommandation.

### Continuité

Exemples :

- arrêter un album sur le téléphone et le reprendre au même endroit sur le Mac ;
- envoyer la lecture du téléphone vers la TV ;
- modifier une playlist sur le desktop puis la retrouver sur Android ;
- consulter la même file depuis une montre ou une télécommande web ;
- changer d'appareil actif sans perdre la position.

### Module central

La synchronisation doit être cachée derrière une petite interface :

```kotlin
interface SyncEngine {
    val state: StateFlow<SyncState>

    suspend fun synchronize(reason: SyncReason): SyncResult
    suspend fun push(change: LocalChange): SyncResult
    suspend fun resolve(conflict: SyncConflict, choice: ConflictChoice)
}
```

L'interface masque :

- versions des documents ;
- reprise après coupure ;
- déduplication ;
- lots de changements ;
- résolution des conflits ;
- chiffrement ;
- migration des schémas ;
- limitation du réseau.

### Sécurité

- compte Jelyvo distinct de la session YouTube ;
- aucun cookie YouTube envoyé en clair entre appareils ;
- secrets chiffrés au repos ;
- jetons révocables et à durée limitée ;
- suppression complète des données depuis le compte ;
- journaux exempts de secrets ;
- synchronisation facultative ;
- mode entièrement local conservé.

## 5. Télécommande web locale

Avant une version web complète, Jelyvo peut exposer une petite interface locale pilotant l'appareil
qui assure réellement la lecture.

### Parcours

1. l'application affiche un QR code ;
2. un navigateur rejoint la session locale ;
3. l'utilisateur confirme l'association ;
4. le navigateur devient une télécommande ;
5. l'appareil principal conserve les flux et les informations sensibles.

### Fonctions

- lecture et pause ;
- précédent et suivant ;
- volume ;
- recherche ;
- ajout à la file ;
- réorganisation de la file ;
- affichage de la pochette et des paroles ;
- choix de l'appareil actif ;
- file collaborative avec votes ;
- révocation immédiate des appareils associés.

Cette solution est plus simple et plus sûre qu'un lecteur web complet. Elle peut également servir de
prototype pour le futur frontend web.

## 6. Wear OS

Le premier lot Wear OS doit rester un compagnon léger.

### MVP compagnon

- titre, artiste et pochette ;
- lecture/pause ;
- précédent/suivant ;
- file d'attente ;
- volume ;
- ajout aux favoris ;
- affichage de la progression ;
- choix rapide d'une playlist ou d'un mix ;
- complication « maintenant en lecture ».

### Deuxième étape

- téléchargement de playlists sur la montre ;
- lecture autonome avec écouteurs Bluetooth ;
- synchronisation de l'historique ;
- purge automatique selon le stockage ;
- reprise entre montre et téléphone.

La lecture autonome est nettement plus coûteuse que la télécommande et doit rester un lot séparé.

## 7. iPhone et iPad

iOS et iPadOS sont faisables après extraction du cœur partagé, mais ne doivent pas être confondus
avec le port macOS Desktop.

### Adapters nécessaires

- moteur AVFoundation ;
- contrôles système MediaPlayer ;
- stockage sécurisé dans le Trousseau ;
- tâches de fond propres à iOS ;
- téléchargements et stockage iOS ;
- Picture-in-Picture ;
- notifications ;
- routage AirPlay ;
- gestion de la session audio.

### Interface

Deux options doivent être prototypées :

- interface SwiftUI consommant le cœur Kotlin partagé ;
- interface Compose Multiplatform avec adaptations Apple.

L'iPad mérite une expérience dédiée :

- navigation en colonnes ;
- file latérale ;
- glisser-déposer ;
- clavier et trackpad ;
- mode Split View ;
- lecteur vidéo redimensionnable.

### CarPlay

CarPlay devient envisageable après stabilisation de l'application iOS :

- bibliothèque audio navigable ;
- playlists et téléchargements ;
- commandes vocales ;
- reprise ;
- interface strictement adaptée à la conduite.

Sa disponibilité dépendra des catégories et autorisations Apple applicables à une application audio.

## 8. Android Auto et conduite

Le socle MediaLibraryService permet d'améliorer l'expérience Android Auto sans attendre les autres
plateformes.

### Améliorations

- arborescence musicale complète ;
- titres aimés ;
- téléchargements ;
- playlists récentes ;
- mix quotidiens ;
- reprise d'écoute ;
- commandes vocales ;
- favoris en un geste ;
- erreurs adaptées à un écran automobile ;
- démarrage rapide avec cache local.

Les écrans et commandes doivent rester simples, prévisibles et utilisables sans détourner
l'attention.

## 9. Continuité et contrôle multi-appareil

La continuité peut devenir le trait distinctif de Jelyvo.

### Capacités

- « lire sur mon Mac » ;
- « lire sur ma TV » ;
- transfert instantané de la file et de la position ;
- appareil actif visible ;
- contrôle depuis le téléphone ou la montre ;
- file partagée ;
- reprise automatique après sortie de veille ;
- détection des appareils du réseau local ;
- association par QR code ;
- arbitrage clair lorsqu'un deuxième appareil démarre une lecture.

Le module de continuité doit manipuler une représentation générique de la session, et non des objets
Media3 :

```kotlin
data class PlaybackSessionSnapshot(
    val sessionId: String,
    val deviceId: String,
    val queue: PlaybackQueue,
    val currentIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean,
    val updatedAt: Long,
)
```

## 10. Qualité audio avancée

### Fonctions

- normalisation ReplayGain ;
- égaliseur mémorisé par périphérique ;
- profils casque, Bluetooth et haut-parleur ;
- choix de qualité par type de réseau ;
- cache prédictif ;
- préchargement du titre suivant ;
- sélection de sortie audio sur desktop ;
- détection de silence ;
- fondu intelligent ;
- gapless prioritaire pour les albums continus ;
- limiteur pour éviter la saturation.

Le comportement de transition peut être automatisé :

- album gapless : aucun fondu ;
- playlist ou radio : crossfade choisi par l'utilisateur ;
- podcast ou contenu long : reprise exacte et aucun crossfade ;
- changement manuel : fondu court facultatif.

## 11. Bibliothèque intelligente

### Organisation

- dossiers de playlists ;
- tags personnels ;
- tri multi-critères ;
- détection des doublons ;
- fusion de playlists ;
- éditions d'un même album regroupées ;
- filtres « jamais écouté », « récemment ajouté » et « téléchargé » ;
- recherche dans les titres aimés et les paroles.

### Playlists intelligentes

Une playlist intelligente est définie par des règles plutôt que par une liste figée :

```text
Artiste suivi
+ ajouté dans les 90 derniers jours
+ écouté moins de 3 fois
+ disponible hors-ligne
```

Le résultat se met à jour automatiquement lorsque la bibliothèque ou l'historique change.

### Recherche

- tolérance aux fautes ;
- recherche phonétique ;
- synonymes ;
- historique local ;
- commandes rapides ;
- recherche dans les paroles ;
- recherche croisée entre titres, albums, artistes, vidéos et playlists.

## 12. Fonctions sociales facultatives

Jelyvo doit rester pleinement utilisable sans fonctions sociales. Celles-ci peuvent néanmoins
enrichir les usages collectifs.

- playlists collaboratives ;
- session d'écoute synchronisée ;
- QR code pour rejoindre une file ;
- votes sur les prochains titres ;
- droits hôte/invité ;
- profil musical privé ou public ;
- partage d'une sélection ou d'un récapitulatif ;
- blocage et révocation des participants.

La file collaborative locale est préférable comme première étape : elle apporte de la valeur sans
construire immédiatement un réseau social.

## 13. Podcasts et contenus longs

Le projet possède déjà des notions de reprise et de playlist podcast. Elles peuvent devenir un mode
spécialisé :

- chapitres ;
- signets ;
- vitesse mémorisée par émission ;
- suppression automatique après écoute ;
- raccourcissement des silences ;
- file séparée de la musique ;
- position synchronisée ;
- transcription ;
- recherche dans un épisode ;
- marqueurs personnels ;
- reprise en tête d'accueil.

Le mode podcast doit réutiliser le moteur de lecture, mais appliquer ses propres règles de
transition, de reprise et de téléchargement.

## 14. Fiabilité, confidentialité et exploitation

Un écosystème multiplateforme exige une exploitation plus rigoureuse.

### Mise à jour

- artefacts signés ;
- canaux stable, bêta et nightly ;
- vérification cryptographique ;
- rollback ;
- changelog par version ;
- compatibilité des schémas de synchronisation.

### Observabilité respectueuse de la vie privée

- crash reports facultatifs ;
- métriques agrégées ;
- mesure du démarrage et de la latence de lecture ;
- aucun titre, cookie ou identifiant de compte dans les journaux ;
- écran local de diagnostic exportable ;
- possibilité de désactiver toute télémétrie.

### Tests

- tests contractuels communs aux adapters de lecture ;
- tests de migration des bases ;
- tests des files et reprises ;
- tests des conflits de synchronisation ;
- tests de navigation clavier/télécommande ;
- tests d'accessibilité ;
- tests sur réseau lent, hors-ligne et URL expirée ;
- validation sur plusieurs tailles d'écran.

## Architecture recommandée

Les plateformes doivent reposer sur quelques modules profonds :

```text
shared-domain
├── Track, Album, Artist et Playlist
├── règles de file
├── historique et statistiques
└── recommandations

shared-catalog
├── recherche
├── pages musicales
├── résolution des flux
└── cache et continuations

shared-playback
├── PlaybackEngine
├── état de lecture
├── reprise
└── règles de transition

shared-library
├── LocalStore
├── sauvegarde
├── playlists intelligentes
└── import/export

shared-sync
├── SyncEngine
├── conflits
├── continuité
└── chiffrement des échanges

platform adapters
├── Android / Media3
├── Desktop
├── Apple / AVFoundation
└── Web / HTMLMediaElement
```

Chaque interface doit cacher la complexité propre à son domaine. Les écrans ne doivent pas connaître
les détails de Media3, Room, AVFoundation ou du protocole de synchronisation.

## Ordre recommandé

### Phase A — Consolider Android

1. interface adaptative téléphone, tablette, pliable et DeX ;
2. navigation clavier et souris ;
3. amélioration Android Auto ;
4. tests de performance et de migration ;
5. poursuite du découpage des fichiers géants.

### Phase B — Extraire le cœur

1. modèle `Track` indépendant d'Android ;
2. `PlaybackEngine` devant Media3 ;
3. `MusicCatalog` devant les appels YouTube ;
4. `LocalStore` devant Room ;
5. règles de file et historique en modules purs ;
6. tests contractuels.

### Phase C — Famille desktop

1. prototype macOS Apple Silicon ;
2. moteur desktop ;
3. expérience grand écran ;
4. paquet macOS signé ;
5. adaptation Windows ;
6. adaptation Linux ;
7. mise à jour automatique.

### Phase D — Continuité

1. modèle de session transférable ;
2. télécommande web locale ;
3. découverte réseau ;
4. transfert téléphone/desktop/TV ;
5. backend Jelyvo Sync facultatif ;
6. résolution des conflits.

### Phase E — Appareils compagnons

1. Android TV ;
2. Wear OS compagnon ;
3. lecture autonome Wear OS ;
4. mode soirée collaboratif.

### Phase F — Nouvelles plateformes

1. prototype web anonyme ;
2. version web/PWA ;
3. prototype iOS/iPadOS ;
4. application iOS ;
5. CarPlay.

## Priorités recommandées

Si l'objectif est d'obtenir rapidement de la valeur sans fragiliser Android :

1. **tablette et Samsung DeX** ;
2. **extraction de `PlaybackEngine`, `Track` et `MusicCatalog`** ;
3. **macOS Apple Silicon** ;
4. **Windows et Linux depuis le même socle desktop** ;
5. **Jelyvo Sync et continuité** ;
6. **télécommande web locale** ;
7. **Android TV et Wear OS** ;
8. **version web complète** ;
9. **iOS/iPadOS puis CarPlay**.

## Conclusion

La direction la plus solide est : **un seul cœur musical, plusieurs expériences adaptées à chaque
écran**.

Le prochain chantier visible devrait améliorer l'interface adaptative Android et Samsung DeX. Le
prochain chantier architectural doit créer les interfaces communes de lecture, catalogue,
bibliothèque et synchronisation. Ces modules fourniront ensuite le levier nécessaire pour produire
macOS, Windows, Linux, TV, Wear OS, web et iOS sans dupliquer progressivement toute l'application.
