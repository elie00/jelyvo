# Publication des mises à jour Jelyvo

Ce document décrit le circuit de publication à reprendre depuis le Mac qui possède la clé ayant
signé les versions publiques de Jelyvo.

## État constaté le 29 juillet 2026

- Le code source est dans `elie00/umihi-music`.
- Les APK publics sont dans
  [`elie00/jelyvo-releases`](https://github.com/elie00/jelyvo-releases/releases).
- L'application vérifie bien les mises à jour via
  `https://api.github.com/repos/elie00/jelyvo-releases/releases/latest`.
- Elle attend un asset nommé exactement `Jelyvo.apk`.
- La dernière version publique stable est `v2.3.3`, publiée le 29 juillet 2026 avec l'asset
  `Jelyvo.apk`.
- `app/build.gradle.kts` annonce `2.3.3` avec le `versionCode` `20303`.
- Le commit de préparation de cette version est `503f2557`.
- `v2.3.2` a été retirée du canal stable et marquée comme pré-release après la découverte d'un
  crash récursif au démarrage. Le correctif est dans `cd88fcd3` et fait partie de `v2.3.3`.
- Le certificat de l'APK public `v2.3.3` a cette empreinte SHA-256 :

  ```text
  b47ac9b86845664530b6e1a77465f210fa7b4913e77b1d44fde6a4f28fbe081d
  ```

- La clé officielle est présente sur le Mac dans
  `/Users/eybo/keystores/jelyvo-release.keystore` et référencée par le fichier local
  `keystore.properties`, ignoré par Git.
- L'APK construit sur le PC du bureau avec son ancienne clé a une autre empreinte :

  ```text
  05c8405692fc3b43ec659d73d5d3db0570dd13d030f188a29d3d306afe13df1d
  ```

  Il ne doit pas être publié : Android refuserait de l'installer par-dessus la version publique.

- Le workflow `.github/workflows/build-release.yml` construit dans `umihi-music`, mais ses actions
  de release publient actuellement dans le dépôt source au lieu de `jelyvo-releases`.
- Le `GITHUB_TOKEN` natif d'un workflow de `umihi-music` ne peut pas écrire dans l'autre dépôt.
- Aucun secret Actions n'est actuellement configuré dans `umihi-music`.
- Les exécutions Actions s'arrêtent avant la première étape à cause du blocage de facturation du
  compte GitHub. Il faut régler ce point avant de compter sur la CI.

## Règle absolue concernant la signature

Une mise à jour Android doit être signée avec la même clé que l'application déjà installée. Le nom
du certificat (`Android Debug`) n'est pas suffisant : seule l'empreinte SHA-256 fait foi.

Ne jamais :

- publier l'APK construit sur le PC du bureau ;
- créer une nouvelle clé pour remplacer la clé existante ;
- committer un fichier `.jks`, `keystore.properties`, un mot de passe ou un token ;
- publier avant d'avoir obtenu exactement l'empreinte `b47ac9b…`.

Le projet ignore déjà `*.jks`, `*.keystore` et `keystore.properties`.

## 1. Préparer le Mac

```bash
git clone https://github.com/elie00/umihi-music.git
cd umihi-music
git switch main
git pull --ff-only
git status --short
```

La dernière commande doit rester vide.

Sur le Mac de publication actuel, la clé officielle est déjà installée. Sur un nouveau poste,
restaurer depuis la sauvegarde la clé qui a servi à publier `v2.3.3`. Si nécessaire :

```bash
find ~ -type f \( -name "*.jks" -o -name "*.keystore" \) 2>/dev/null
```

Vérifier son certificat avant de la copier :

```bash
keytool -list -v -keystore /chemin/vers/la-cle.jks
```

La ligne SHA-256 doit correspondre à :

```text
B4:7A:C9:B8:68:45:66:45:30:B6:E1:A7:74:65:F2:10:
FA:7B:49:13:E7:7B:1D:44:FD:E6:A4:F2:8F:BE:08:1D
```

Copier ensuite la clé localement, sans l'ajouter à Git :

```bash
mkdir -p ~/keystores
cp /chemin/vers/la-cle.jks ~/keystores/jelyvo-release.keystore
```

Créer `keystore.properties` à la racine :

```properties
storeFile=/chemin/absolu/vers/jelyvo-release.keystore
storePassword=REMPLACER
keyAlias=REMPLACER
keyPassword=REMPLACER
```

Vérifier immédiatement que ces fichiers sont bien ignorés :

```bash
git status --short --ignored keystore.properties
```

Ils doivent apparaître comme ignorés (`!!`), jamais comme fichiers à committer.

## 2. Préparer la prochaine version

La version qui suit `v2.3.3` doit utiliser au minimum le `versionCode` `20304`. Par exemple, pour
préparer `v2.3.4` :

Dans `app/build.gradle.kts`, modifier :

```kotlin
val appVersionName = "2.3.4"
val appVersionCode = 20304
```

Le `versionCode` doit toujours augmenter, même si le nom de version change autrement.

Puis créer un micro-commit :

```bash
git add app/build.gradle.kts
git commit -m "chore(release): prepare 2.3.4"
git push origin main
```

## 3. Tester et construire l'APK officiel

Le S24 Ultra utilise `arm64-v8a` :

```bash
./gradlew \
  testStandaloneDebugUnitTest \
  compileStandaloneDebugAndroidTestKotlin \
  assembleStandaloneRelease \
  -Pbeta=false \
  -PtargetAbi=arm64-v8a \
  -PgitHash="$(git rev-parse HEAD)"
```

L'APK attendu est :

```text
app/build/outputs/apk/standalone/release/Jelyvo.apk
```

Vérifier la signature avec le `apksigner` du SDK Android :

```bash
"$ANDROID_HOME/build-tools/37.0.0/apksigner" \
  verify --verbose --print-certs \
  app/build/outputs/apk/standalone/release/Jelyvo.apk
```

Ne continuer que si :

- `Verifies` est affiché ;
- la signature v2 est valide ;
- le digest SHA-256 du signataire est exactement `b47ac9b…`.

Contrôler aussi l'APK :

```bash
shasum -a 256 app/build/outputs/apk/standalone/release/Jelyvo.apk
unzip -l app/build/outputs/apk/standalone/release/Jelyvo.apk | grep 'lib/'
```

Les bibliothèques natives ne doivent exister que sous `lib/arm64-v8a/`.

## 4. Publication manuelle sûre

La publication manuelle est le chemin le plus simple tant que GitHub Actions et ses secrets ne sont
pas réparés.

Vérifier l'authentification :

```bash
gh auth status
```

Créer la release publique :

```bash
gh release create v2.3.4 \
  app/build/outputs/apk/standalone/release/Jelyvo.apk \
  --repo elie00/jelyvo-releases \
  --title "Jelyvo v2.3.4" \
  --notes-file /chemin/vers/les-notes.md \
  --latest
```

L'asset doit conserver exactement le nom `Jelyvo.apk`.

Vérifier ensuite :

```bash
gh release view v2.3.4 \
  --repo elie00/jelyvo-releases \
  --json tagName,name,isDraft,isPrerelease,assets,url

curl -fsSL \
  https://api.github.com/repos/elie00/jelyvo-releases/releases/latest

curl -I -L \
  https://github.com/elie00/jelyvo-releases/releases/latest/download/Jelyvo.apk
```

Enfin, sur le S24 Ultra :

1. ouvrir Jelyvo déjà installé ;
2. aller dans Réglages > Mises à jour ;
3. lancer une vérification manuelle ;
4. télécharger et installer la nouvelle version sans désinstaller `2.3.3` ;
5. vérifier que bibliothèque, téléchargements, historique et connexion sont conservés.

Si Android affiche `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ou demande de désinstaller l'application,
arrêter immédiatement : la signature ne correspond pas.

## 5. Automatiser avec GitHub Actions

### 5.1 Débloquer Actions

Corriger d'abord le problème de facturation GitHub. Les runs actuels échouent avant toute étape :
modifier le YAML ne suffira pas tant que ce blocage existe.

### 5.2 Secrets de signature dans `umihi-music`

Depuis le Mac, encoder la clé et créer le secret sans écrire la valeur dans un fichier suivi :

```bash
base64 < ~/keystores/jelyvo-release.keystore | tr -d '\n' | \
  gh secret set SIGNING_KEY --repo elie00/umihi-music

gh secret set KEY_STORE_PASSWORD --repo elie00/umihi-music
gh secret set ALIAS --repo elie00/umihi-music
gh secret set KEY_PASSWORD --repo elie00/umihi-music
```

Les trois dernières commandes demandent la valeur dans le terminal.

### 5.3 Token limité au dépôt de releases

Créer sur GitHub un fine-grained personal access token :

- propriétaire : `elie00` ;
- dépôt sélectionné uniquement : `jelyvo-releases` ;
- permission `Contents: Read and write` ;
- durée de vie limitée et renouvelable.

L'enregistrer dans le dépôt source :

```bash
gh secret set RELEASE_REPO_TOKEN --repo elie00/umihi-music
```

Ne jamais réutiliser ce token dans l'application Android.

### 5.4 Corriger le workflow

Dans `.github/workflows/build-release.yml`, les étapes de publication doivent utiliser le token
inter-dépôts et cibler explicitement `elie00/jelyvo-releases`.

Exemple pour une release stable :

```yaml
- name: Publish in the public releases repository
  env:
    GH_TOKEN: ${{ secrets.RELEASE_REPO_TOKEN }}
    VERSION: ${{ steps.get_version.outputs.version }}
  run: |
    gh release create "v${VERSION}" \
      ./artifacts/**/Jelyvo.apk \
      --repo elie00/jelyvo-releases \
      --title "Jelyvo v${VERSION}" \
      --generate-notes \
      --latest
```

Pour la bêta, utiliser le même `--repo`, un tag `beta`, et `--prerelease`. L'API
`releases/latest` utilisée par l'application ignore normalement les pré-releases : seule une
release stable publiée doit être proposée aux utilisateurs du canal actuel.

Ajouter également une étape de contrôle de certificat avant la publication :

```yaml
- name: Verify release certificate
  run: |
    digest=$("$ANDROID_HOME/build-tools/37.0.0/apksigner" \
      verify --print-certs ./artifacts/**/Jelyvo.apk |
      sed -n 's/.*certificate SHA-256 digest: //p')
    test "$digest" = \
      "b47ac9b86845664530b6e1a77465f210fa7b4913e77b1d44fde6a4f28fbe081d"
```

Cette étape doit échouer fermement si la mauvaise clé est utilisée.

## 6. Contrôles après automatisation

Après le premier run vert :

```bash
gh run list \
  --repo elie00/umihi-music \
  --workflow build-release.yml \
  --limit 5

gh release list --repo elie00/jelyvo-releases --limit 5
```

Contrôler systématiquement :

- version supérieure à celle déjà publiée ;
- asset nommé `Jelyvo.apk` ;
- certificat `b47ac9b…` ;
- release stable non brouillon pour qu'elle devienne `latest` ;
- téléchargement public sans token ;
- mise à jour réelle sur le téléphone sans désinstallation.

## Résumé opérationnel

Le circuit manuel est opérationnel : la clé officielle est installée sur le Mac, `v2.3.3` est la
release stable courante et son asset possède le bon nom et le bon certificat.

Les deux chantiers restants concernent l'automatisation :

1. réparer la facturation et configurer les secrets GitHub Actions ;
2. faire publier le workflow explicitement dans `elie00/jelyvo-releases`, pas dans le dépôt source.

En attendant l'automatisation, une publication manuelle avec `gh release create` est sûre à
condition d'avoir vérifié la version et la signature officielle.
