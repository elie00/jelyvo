# Jelyvo pour Galaxy Watch

Le module `wear` est le compagnon Wear OS du lecteur Android. La version stable vise la
Galaxy Watch 8 et reste compatible avec les montres Wear OS 3 ou plus récentes (API 30+).

## Fonctionnalités actuelles

- état de connexion au téléphone ;
- pochette, titre et artiste en cours ;
- progression interpolée sur la montre sans trafic permanent ;
- lecture/pause, titre précédent et titre suivant ;
- recul/avance de 10 secondes, favoris, lecture aléatoire et répétition ;
- réglage du volume du téléphone ;
- file d'attente navigable au toucher ou avec la molette, avec sélection directe d'un titre ;
- interface en français et en anglais avec retours d'erreur explicites ;
- mode ambiant basse consommation ;
- tuile Jelyvo ajoutable au carrousel Wear OS pour voir le dernier titre et ouvrir le lecteur ;
- commandes disponibles lorsque l'écran du téléphone est éteint grâce à la session Media3.

La musique continue à être diffusée par le téléphone. Cette architecture conserve les comptes,
les téléchargements, la file d'attente et le moteur YouTube dans un seul endroit, tout en gardant
une application montre légère.

## Modules

- `app` publie l'état du lecteur et reçoit les commandes de la montre ;
- `wear` contient l'application Wear OS et son interface ronde ;
- `wear-shared` contient le contrat stable des messages et des instantanés.

La liaison utilise la Data Layer de Google Play services. Les APK téléphone et montre doivent
donc avoir le même `applicationId` et être signés avec le même certificat. Les configurations
`release` des deux modules réutilisent la même clé définie dans `keystore.properties`.

## Construction locale

```shell
./gradlew :app:assembleStandaloneRelease :wear:assembleRelease
```

Les APK générés sont :

- `app/build/outputs/apk/standalone/release/Jelyvo.apk` ;
- `wear/build/outputs/apk/release/Jelyvo-Watch.apk`.

Installez d'abord la mise à jour Jelyvo sur le téléphone, puis envoyez l'APK montre avec Android
Studio, ADB Wi-Fi ou l'outil de débogage Wear OS de Samsung.

## Utilisation

Glissez horizontalement depuis le lecteur pour ouvrir les commandes, puis la file d'attente.
La couronne ou la lunette rotative fait défiler la file. Pour ajouter la tuile, effectuez un appui
long sur le cadran, ouvrez **Tuiles**, puis choisissez **Lecteur Jelyvo**.

La lecture reste volontairement pilotée par le téléphone : le compte, le cache, les téléchargements
et le moteur YouTube restent ainsi centralisés, sans dupliquer des données sensibles sur la montre.
