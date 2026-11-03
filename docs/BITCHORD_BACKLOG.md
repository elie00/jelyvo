# Suite de la 2.6.13 et reprises possibles depuis BitChord

> Notes du 2 novembre 2026, complétées le 3 novembre.

## État

La v2.6.13 est publiée sur `elie00/jelyvo-releases`. L'APK est signé avec la clé officielle
(`b47ac9b…`). `feat/bitchord-port-v2` et `codex/finish-existing-platforms` sont fusionnées dans
`main`, et la PR #1 est close.

## Fait le 3 novembre, sur `feat/jam-complete`

- **Écoute à plusieurs** : membres, exclusion, contrôle réservé à l'hôte, lien d'invitation et QR code, ouverture
  depuis un lien (avec confirmation, et un avertissement si le serveur n'est pas celui par défaut), file
  partagée, message quand une partie se termine.
- **Correctif** : une partie disparue du serveur relançait une reconnexion toutes les 10 s, sans fin, ce qui
  consommait le quota gratuit. Elle se termine désormais.
- **Gains rapides** : saut des silences (suspendu pendant une partie, désactive le déchargement audio), carte
  image pour les paroles, « Ouvrir avec » pour les fichiers audio, rafraîchissement le plus rapide.
- **Déjà présent** : l'arrêt de la minuterie en fin de titre (`startSleepTimerEndOfSong`). Seule la durée libre manque.

**À tester en priorité sur deux téléphones** : la file partagée. Sa logique est testée, mais jamais avec de vrais
lecteurs. Surveiller les deux appareils en lecture automatique, qui peuvent proposer chacun leur suite.

## Fait le 3 novembre, sur `feat/bitchord-components`

- **Interface alignée sur BitChord** : feuilles du bas, menus, rangées, cartes, boutons, indicateurs, volets grand
  écran, pages teintées par la pochette, icônes de la barre d'état selon la photo d'artiste.
- **Traduction des paroles**, à la demande. Les paroles ne partent chez Google que si on appuie sur le bouton, par
  un service web non officiel qui peut cesser de marcher. Le cache reste en mémoire, et le nouvel essai sur les
  paroles en alphabet latin de BitChord n'est pas repris.
- **Minuterie** : durée libre, de 1 minute à 24 heures.
- **Bibliothèque locale** : navigation par dossiers et paroles intégrées (MP3, FLAC, M4A). Les fichiers Ogg et
  Matroska ne sont pas lus. Les pochettes intégrées passent par MediaStore, sans le lecteur de BitChord : à vérifier.
- **Replay** : version compacte, sur l'année en cours, avec des histoires plein écran et une affiche à partager.
  Pas de choix de période ni de cartes multiples comme dans BitChord.

## Testé le 3 novembre sur le Mac

Tout ce qui précède a tourné sur deux émulateurs : résultats et reste à faire dans [`CHANTIER.md`](../CHANTIER.md).
Le code source est désormais publié dans `elie00/jelyvo`. PoToken n'est pas écrit, par choix : il sert à
contourner la détection de robots.

## Résultats des tests du 2 novembre

Sur émulateur, avec un APK de release signé, installé par-dessus la 2.6.12 publique.

| Test | Résultat | Constat |
|---|---|---|
| Mise à jour depuis la 2.6.12 | OK | Pas de désinstallation ni de plantage. Le schéma Room n'a pas changé. |
| Recherche, lecture, paroles synchronisées | OK | Le client `ANDROID_VR` est refusé par YouTube, le repli prend le relais. |
| Fondu enchaîné | OK | Deux lecteurs audio actifs pendant ≈ 6 s, la durée réglée, puis un seul. |
| Automix | En partie | Transition sans plantage, mais rien ne prouve que l'analyse a changé le fondu. |
| Écoute à plusieurs | OK | Titre, pause, reprise et saut passent dans les deux sens, avec un second appareil simulé par un script sur le protocole réel. |

Deux bugs trouvés ainsi sont corrigés dans la 2.6.13 : l'artiste « Song » dans la recherche, et la
partie créée pendant une écoute qui ne partageait rien.

**Reste à tester** :

- le rendu à l'oreille, sur un vrai téléphone ;
- l'écoute à plusieurs entre deux vrais téléphones, avant de retirer `backend/` ;
- l'effet réel de l'analyse d'Automix. Elle ne journalise rien : il faudra ajouter un journal dans un
  build de debug. En données mobiles, elle ne lit que les fichiers déjà sur l'appareil ;
- le scrobbling avec de vrais comptes ;
- les écrans secondaires : téléchargements, ambiances, import, égaliseur, statistiques.

## Reprises possibles depuis BitChord

Source : [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord), GPL-3.0, examiné au
commit `b690dc1` (v1.9). Conserver le crédit dans l'en-tête des fichiers adaptés et dans le README.
Les chemins ci-dessous partent de `app/src/main/java/com/music/bitchord/`, sauf mention contraire.

### À prendre en priorité

| Fonction | Dans BitChord | Dans Jelyvo aujourd'hui | Effort |
|---|---|---|---|
| Écoute à plusieurs complète : lien d'invitation, QR code, membres, exclusion, contrôle réservé à l'hôte, file partagée | `ui/screens/ListenTogetherScreen.kt`, `ui/screens/ListenTogetherSheets.kt`, `data/listentogether/ListenTogether.kt`, `playback/PartySync.kt`, `ui/components/QrCode.kt` (`com.google.zxing:core:3.5.3`) | `JamManager` n'envoie que lecture, pause, saut et titre. `worker/` gère déjà le reste, dont `/invite/{code}`. Le manifeste n'a pas de lien pour les invitations. | Moyen |
| PoToken | `data/innertube/potoken/PoTokenWebView.kt` | Absent. Le repli marche encore, mais c'est le point le plus fragile. | Moyen |
| Carte image pour partager les paroles | `ui/player/LyricsShareCard.kt`, `ui/player/AndroidLyricsShareSheet.kt` | `ShareHelper.shareLyrics` n'envoie que du texte. | Faible |
| Traduction des paroles | `docs/LYRICS_TRANSLATION.md` et `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsTranslation.kt` (depuis la racine du dépôt) | Faite (voir plus haut). | Faible à moyen |
| Saut des silences | Réglage Media3 `skipSilenceEnabled` | Absent. | Faible |
| Arrêt de la minuterie en fin de titre, durée libre | Notes de la v1.9 | Faits. | Faible |
| « Ouvrir avec » pour les fichiers audio | Notes de la v1.9 | Le manifeste ne déclare aucun type audio. | Faible |
| Choix du taux de rafraîchissement (120 Hz) | `ui/performance/DisplayRefreshRates.kt` | Absent. | Faible |
| Replay, rétrospective façon « Wrapped » | `ui/replay/`, `data/stats/ListeningStats.kt` | Version compacte faite (voir plus haut). | Moyen |
| Bibliothèque locale : dossiers, pochettes et paroles intégrées aux fichiers | `data/LocalMediaRepository.kt`, `data/LocalGroups.kt`, `data/remote/EmbeddedArt.kt`, `data/lyrics/EmbeddedLyrics.kt` | Dossiers et paroles faits (voir plus haut). | Moyen |

Points d'attention :

- **PoToken** : le code exécute dans une WebView le JavaScript BotGuard servi par Google. La correction
  doit aussi être reportée à la main dans Umihi Tube, qui a sa propre couche d'extraction.
- **Traduction** : elle passe par un service web Google non officiel, qui peut cesser de fonctionner,
  et envoie les paroles à Google.
- **Saut des silences** : le `CrossfadeController` de BitChord tient compte de ce réglage. Vérifier
  son effet sur le fondu de Jelyvo avant de l'activer en même temps.
- **Invitations** : il faudra déclarer l'adresse du serveur dans le manifeste, et gérer le cas où
  l'utilisateur a choisi un autre serveur.

### Gros chantiers, à décider

- **Automix complet, calage sur le tempo.** Il faut `beat_this_final0_q8.onnx` (23 Mo),
  `vocals_umxhq_int8.onnx` (9,1 Mo), ONNX Runtime et l'analyseur natif (`native/analyzer/`, depuis la
  racine du dépôt, sous AGPL), pour un APK de 6,7 Mo aujourd'hui. Il faudrait télécharger les modèles
  à la demande, et seule une écoute réelle permettra de juger le résultat.
- **Application Android TV.** `tv/` compte 40 fichiers Kotlin et s'appuie sur `shared/` et `sharedUi/`
  (220 fichiers), une séparation que Jelyvo n'a pas sous cette forme.
- **Audio avancé.** `playback/EqualizerProcessor.kt` avec limiteur, `playback/SpatialAudioProcessor.kt`,
  `playback/LoudnessProcessor.kt`, et la sortie USB `playback/audio/bitperfect/`. Surtout utile aux
  audiophiles.

### À éviter

- **Modules de sources tierces** (`data/sources/`) : ils exécutent du code externe via QuickJS, et le
  code cite Tidal, Qobuz et Deezer. Risque de sécurité et risque juridique.
- **Pochettes animées** (`data/canvas/`) : elles reposent sur un cookie Spotify `sp_dc` et sur un jeton
  Apple récupéré hors API officielle. C'est fragile.
- **Discord Rich Presence** (`data/discord/DiscordRPC.kt`) : la connexion se fait avec le compte de
  l'utilisateur, ce qui peut faire bannir ce compte.

## Ménage

- Branches distantes déjà fusionnées et supprimables : `release/2.6.13`, `feat/bitchord-port-v2`,
  `codex/finish-existing-platforms`, `agent/finalize-m3-experience`.
- `feat/bitchord-port` n'est pas fusionnée, et ne doit pas l'être : elle part de la 2.3.1. Ses
  corrections utiles sont déjà couvertes, et ses notes de suivi n'ont pas été comparées une à une.
- La CI exécute maintenant les tests de `worker/` et de `backend/` (tâche `servers` de `verify.yml`, pas encore passée).
- CodeQL échoue sur chaque commit : l'analyse de code n'est pas activée pour ce dépôt privé. Rendre le dépôt public réglerait aussi ce point.
  le worker).
