# Audit et consolidation Jelyvo — 8 octobre 2026

Périmètre demandé : les trois plateformes existantes, Android, Wear OS et macOS Apple Silicon.
Point de départ : `39a20c2077f310eb660928c25102cc9f5a0c1acf`. Les projets Web, iOS, TV, Windows,
Linux et Sync décrits dans les feuilles de route ne sont pas des applications livrées par ce dépôt.

## Architecture examinée

| Module | Responsabilité | Vérification |
|---|---|---|
| `app` | Client Android, Media3/Cast, Room, sauvegarde, bibliothèque, import public | Tests JVM, lint, APK debug/release et migrations sur émulateur |
| `wear` | Télécommande et lecteur autonome, téléchargements, stockage, Tile | Tests JVM, lint, APK debug/release et stockage sur émulateur Wear |
| `wear-shared` | Protocole de données téléphone/montre | Tests JVM du contrat |
| `music-core` | Identité musicale, catalogue, lecture, flux | Tests JVM indépendants d'Android |
| `music-youtube` | InnerTube, session et extraction NewPipe partagées | Tests de parsing et résolution locale |
| `music-ui` | Thème, typographie et pochettes communs | Compilation Android et desktop |
| `desktopApp` | Client Compose macOS, lecture JavaFX, Trousseau | Tests JVM, paquet natif, recherche et lecture réelles |
| `baselineprofile` | Génération et benchmarks de profils Android | Configuration examinée ; nouveaux benchmarks non exécutés |

Le seul élément local préexistant non suivi était `.claude/` ; il est conservé hors des commits.
Le dépôt GitHub reste privé.

## Corrections et fonctions terminées

### macOS

- Le lecteur sérialise toutes les commandes et les callbacks JavaFX. Une requête remplacée, une
  erreur tardive ou un ancien lecteur ne peut plus modifier le titre courant.
- La pause pendant la résolution est respectée ; une erreur est visible et le bouton Lecture
  permet de réessayer. Le lecteur et ses coroutines sont libérés à la fermeture.
- Le mode aléatoire parcourt réellement les emplacements de la file, y compris les doublons ;
  précédent/suivant, répétition, volume, retrait et réordonnancement sont utilisables.
- Accueil local, historique, favoris, création de playlists depuis la file, renommage et
  suppression. Le JSON est écrit par remplacement atomique ; un fichier illisible ou d'une
  version inconnue n'est jamais écrasé silencieusement. Les écritures concurrentes sont testées.
- Les recherches et collections obsolètes sont annulées ; Entrée lance la recherche. L'onglet
  Vidéos est exposé. Les liens de playlists YouTube sont reconnus et les pages suivantes des
  collections sont parcourues.
- Cmd/Ctrl+Espace : lecture/pause ; gauche/droite : précédent/suivant ; 1–4 : accueil, recherche,
  bibliothèque, file ; Q/W : quitter/fermer. Le routage se fait au niveau de la fenêtre et
  reconnaît les chiffres produits avec Maj sur AZERTY. La fenêtre a une taille minimale utilisable.
- Les en-têtes de session incomplets ou contenant des caractères de contrôle sont refusés avant
  stockage. La déconnexion ne prétend plus réussir lorsque la suppression du Trousseau échoue.
  Les processus `security` ont un délai maximal et ne reçoivent pas le secret dans leurs arguments.
- Lecture des titres aimés du compte depuis l'écran Compte, avec pagination. Cette collection
  distante n'est pas fusionnée avec les favoris locaux. Aucun cookie réel n'est requis par les tests.
- Le résolveur ne retombe plus sur un conteneur que JavaFX ne sait pas lire lorsqu'aucun MP4
  progressif compatible n'est disponible ; les fichiers locaux deviennent des URI valides.

### Android

- L'accueil conserve historique, reprise, raccourcis et mixes locaux après déconnexion, pendant
  le chargement ou si le réseau échoue. L'état distant original reste disponible pour le diagnostic.
- La propagation de l'annulation est préservée lors du rafraîchissement.
- Les nouvelles chaînes sont présentes en français et en anglais.
- Versions Android et Watch alignées sur `2.6.13` ; la construction téléphone reste bêta par défaut.

### Wear OS

- Huit erreurs lint d'utilisation des API Media3 corrigées ; focus audio actif ; contrôleurs
  externes limités à l'application et aux contrôleurs approuvés par Android.
- Les identifiants utilisés comme noms de fichiers sont strictement validés. Les titres locaux
  du téléphone ne sont pas envoyés au résolveur YouTube de la montre.
- Publication atomique des téléchargements, fichiers temporaires propres à chaque worker,
  vérification des octets reçus, limite de 128 Mio par titre et suppression du temporaire en échec.
- Une génération par bibliothèque et par titre empêche un travail ancien de rétablir un fichier
  supprimé. Une nouvelle instance peut réindexer un fichier terminé après un arrêt du processus.
- Les clics répétés ne remplacent plus un téléchargement actif ; annuler ou tout effacer invalide
  également les travaux. Les écritures concurrentes des favoris ne se perdent plus.
- Le relais ne confirme son succès qu’une fois le titre attendu réellement en lecture sur la
  destination ; un échec ou une expiration conserve la source en lecture.
- La langue des requêtes suit les changements de locale de l'application.

### Construction et CI

- La JVM Gradle exige Java 21 sans imposer un ancien téléchargement JetBrains/Foojay. Le dernier
  échec distant du build provenait précisément d'une URL Foojay renvoyant HTTP 400.
- `verify.yml` vérifie les tests et lint Android/Watch, les APK de développement, les tests desktop
  et le DMG sur Apple Silicon. Le choix du runner suit la
  [référence GitHub](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
- CodeQL construit sans secret de signature. Le dépôt privé doit encore avoir Code scanning activé
  pour accepter ses résultats : les runs `37638399986` et `37695448868` confirment ce blocage
  de configuration lors de l’envoi des résultats Actions.
- L'ancien workflow de publication visait le dépôt privé des sources, alors que l'updater consulte
  `elie00/jelyvo-releases`. Le workflow produit désormais manuellement des artefacts signés et
  vérifie le certificat des deux APK. La publication reste assurée par `publish-release.sh`,
  dont les tests et le lint précèdent maintenant le build. Aucune release publique n'a été créée.
- Le README et les guides ont été corrigés : deux langues réellement embarquées, import par liens
  publics déjà existant, lecture autonome Watch et fonctions macOS effectives.

## Preuves locales

- **459 tests JVM** : Android 410, Watch 2, cœur 17, YouTube 6, protocole Wear 9, desktop 15.
- **10 tests instrumentés** : 4 sur téléphone API 36 ARM64, dont les migrations Room 16→17,
  17→18 et 16→18 ; 6 sur Wear API 36 ARM64, dont purge pendant téléchargement, publication,
  récupération d'index et concurrence entre instances.
- Lint Android et Wear : **zéro erreur**. Des avertissements subsistent, notamment des dépréciations
  et conseils de style ; ils ne sont pas masqués par une nouvelle baseline.
- APK debug et release minifiés construits pour Android et Watch ; certificat des deux releases
  conforme à l'empreinte pérenne `b47ac9b86845664530b6e1a77465f210fa7b4913e77b1d44fde6a4f28fbe081d`.
- DMG et bundle Apple Silicon construits. Le client empaqueté a effectué la recherche « Daft Punk »
  puis lu « Instant Crush (feat. Julian Casablancas) » : progression observée de 0:05 à 0:55,
  pause à 0:58, durée annoncée 5:37, file de 20 titres affichée. Dans le bundle final, l’album
  « Random Access Memories » charge 13 titres ; « Give Life Back to Music » progresse jusqu’à
  0:19 avant pause. La file enregistrée en playlist est retrouvée après redémarrage.
  Les raccourcis de recherche, bibliothèque et fermeture sont vérifiés avec le clavier AZERTY.
- Aller-retour du Trousseau vérifié avec un secret factice dans une entrée temporaire distincte,
  supprimée immédiatement après le test ; aucun compte utilisateur modifié.
- `actionlint` et `bash -n scripts/publish-release.sh` vérifient la syntaxe des workflows/scripts.

Deux essais de tests téléphone ont expiré pendant la décompression/initialisation des DEX par
Android, avant le démarrage applicatif, alors que les deux émulateurs et les compilations tournaient.
Après arrêt de l'émulateur Watch et précompilation des APK de test, les quatre tests ont réussi.
Cela ne constitue pas une mesure des performances sur un téléphone physique.

## Limites de livraison

Cette consolidation n'est pas une déclaration d'achèvement de toutes les feuilles de route.

- Le DMG reste une préversion locale, sans signature Developer ID ni notarisation Apple.
- Now Playing natif, touches multimédias système, AirPlay et téléchargement macOS restent absents.
- L'édition du contenu des playlists **YouTube distantes**, la synchronisation des collections
  locales entre Mac et téléphone et l'OAuth des bibliothèques musicales privées restent absents.
- Le nouveau lecteur de titres aimés du compte est testé sur des fixtures, pas avec un compte réel.
  Les modifications de pagination ne garantissent pas la disponibilité future des API YouTube.
- Pairage physique téléphone/Watch, sortie audio Bluetooth Wear, Chromecast, Android Auto,
  restauration entre appareils et campagne de performances S24 Ultra restent à qualifier.
- La CI distante est à distinguer des tests locaux : consulter le run attaché au commit livré.
  Activer Code scanning ou fournir une identité Apple appropriée relève de la configuration du
  compte, et n'a pas été effectué par cet audit.

## Reproduire

```sh
./gradlew :music-core:test :music-youtube:test :wear-shared:test \
  :app:testStandaloneDebugUnitTest :wear:testDebugUnitTest :desktopApp:test \
  :app:lintStandaloneDebug :wear:lintDebug --console=plain

./gradlew :app:assembleStandaloneRelease :wear:assembleRelease :desktopApp:packageDmg \
  -PgitHash="$(git rev-parse --short HEAD)" --console=plain
```

Les tests instrumentés exigent une cible adaptée pour chaque APK (même identifiant d'application
sur téléphone et montre) ; exécuter les suites sur des émulateurs dédiés séparés. Les fichiers de
sortie restent sous `app/build/outputs`, `wear/build/outputs` et `desktopApp/build/compose/binaries`.
