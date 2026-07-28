#!/bin/bash
# Publie une release signée de Jelyvo sur le dépôt public elie00/jelyvo-releases.
#
# Usage :
#   scripts/publish-release.sh ["notes de version en markdown"]
#
# - La version vient de appVersionName dans app/build.gradle.kts (à bumper avant).
# - Le build est signé via keystore.properties -> ~/keystores/jelyvo-release.keystore ;
#   le script refuse de publier si l'empreinte du certificat ne correspond pas à celle
#   de l'app installée (sinon les utilisateurs devraient désinstaller/réinstaller).
# - Sans argument, les notes sont ouvertes dans $EDITOR.
set -euo pipefail
cd "$(dirname "$0")/.."

RELEASES_REPO="elie00/jelyvo-releases"
APK="app/build/outputs/apk/standalone/release/Jelyvo.apk"
EXPECTED_CERT_SHA256="b47ac9b86845664530b6e1a77465f210fa7b4913e77b1d44fde6a4f28fbe081d"

version=$(grep -E 'val\s+appVersionName\s*=' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
[[ -n "$version" ]] || { echo "appVersionName introuvable dans app/build.gradle.kts" >&2; exit 1; }
tag="v$version"
git_hash=$(git rev-parse --short HEAD)

[[ "$(git branch --show-current)" == "main" ]] || {
    echo "La release stable doit être construite depuis main." >&2
    exit 1
}
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Le dépôt contient des changements suivis non commités." >&2
    exit 1
fi

if gh release view "$tag" --repo "$RELEASES_REPO" >/dev/null 2>&1; then
    echo "La release $tag existe déjà sur $RELEASES_REPO — bumper appVersionName d'abord." >&2
    exit 1
fi

[[ -f keystore.properties ]] || { echo "keystore.properties manquant : le build serait signé avec la clé debug du poste." >&2; exit 1; }

echo "== Build $tag (release signée, -Pbeta=false) =="
./gradlew assembleStandaloneRelease -Pbeta=false -PgitHash="$git_hash" --console=plain

[[ -f "$APK" ]] || { echo "APK introuvable : $APK" >&2; exit 1; }

echo "== Vérification de la signature =="
apksigner=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
actual=$("$apksigner" verify --print-certs "$APK" | sed -nE 's/.*SHA-256 digest: ([0-9a-f]+).*/\1/p' | head -1)
if [[ "$actual" != "$EXPECTED_CERT_SHA256" ]]; then
    echo "Empreinte de signature inattendue : $actual" >&2
    echo "Attendu : $EXPECTED_CERT_SHA256 (clé ~/keystores/jelyvo-release.keystore)" >&2
    exit 1
fi
echo "Signature OK ($actual)"

notes="${1:-}"
if [[ -z "$notes" ]]; then
    notes_file=$(mktemp /tmp/jelyvo-notes.XXXXXX.md)
    printf -- "- \n" > "$notes_file"
    "${EDITOR:-nano}" "$notes_file"
    notes=$(cat "$notes_file")
fi

echo "== Publication de $tag sur $RELEASES_REPO =="
gh release create "$tag" "$APK" \
    --repo "$RELEASES_REPO" \
    --title "Jelyvo $tag" \
    --notes "$notes"

echo "Publié : https://github.com/$RELEASES_REPO/releases/tag/$tag"
echo "Les applications installées proposeront la mise à jour au prochain lancement."
