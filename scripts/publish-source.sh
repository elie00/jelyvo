#!/bin/bash
# Publie le code source de main sur le dépôt public elie00/jelyvo.
#
# Usage :
#   scripts/publish-source.sh [--dry-run]
#
# Le travail se fait sur une copie jetable : ni ce dépôt ni son historique ne sont touchés.
# La copie est réécrite avant publication :
# - toute identité absente de KEPT_IDENTITIES devient celle du compte public, pour que les
#   commits faits depuis un poste de travail n'exposent ni son nom ni son adresse ;
# - les chemins C:\Users\<nom>\ perdent leur nom d'utilisateur, dans tout l'historique.
#
# Ces règles donnent toujours le même résultat, donc les commits déjà publics gardent leur
# empreinte et une publication ne fait qu'ajouter les nouveaux. Le script refuse tout le reste et
# ne force jamais : si cette vérification échoue, c'est que les règles ont changé, et réécrire un
# dépôt public doit rester une décision prise en connaissance de cause.
#
# Ce fichier est publié avec le reste : ne jamais y écrire le nom ou l'adresse à masquer.
# Prérequis : git-filter-repo et gitleaks (brew install git-filter-repo gitleaks).
set -euo pipefail
cd "$(dirname "$0")/.."

PUBLIC_URL="https://github.com/elie00/jelyvo.git"
PUBLIC_NAME="elie00"
PUBLIC_EMAIL="elieyvon.b.o@gmail.com"
KEPT_IDENTITIES=(
    "$PUBLIC_EMAIL"
    "62717259+ilianoKokoro@users.noreply.github.com"
    "iliandelagrange@gmail.com"
    "49699333+dependabot[bot]@users.noreply.github.com"
    "41898282+github-actions[bot]@users.noreply.github.com"
    "IzzySoft@users.noreply.github.com"
    "noreply@github.com"
)

dry_run=false
case "${1:-}" in
    "") ;;
    --dry-run) dry_run=true ;;
    *) echo "Usage : $0 [--dry-run]" >&2; exit 2 ;;
esac

command -v git-filter-repo >/dev/null || { echo "git-filter-repo manquant : brew install git-filter-repo" >&2; exit 1; }
command -v gitleaks >/dev/null || { echo "gitleaks manquant : brew install gitleaks" >&2; exit 1; }

[[ "$(git branch --show-current)" == "main" ]] || {
    echo "Le code public se publie depuis main." >&2
    exit 1
}
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Le dépôt contient des changements suivis non commités." >&2
    exit 1
fi
git fetch --quiet origin main
[[ "$(git rev-parse HEAD)" == "$(git rev-parse FETCH_HEAD)" ]] || {
    echo "main n'est pas identique à origin/main : pousser ou récupérer d'abord." >&2
    exit 1
}

work=$(mktemp -d "${TMPDIR:-/tmp}/jelyvo-public.XXXXXX")
trap 'rm -rf "$work"' EXIT

echo "== Copie de main =="
git clone --quiet --no-local --single-branch --branch main . "$work/source"
cd "$work/source"

echo "== Réécriture de la copie =="
kept=$(printf 'b"%s",' "${KEPT_IDENTITIES[@]}")
cat > "$work/replacements.txt" <<'RULES'
regex:C:\\Users\\[A-Za-z0-9._-]+\\==>C:\\Users\\<user>\\
RULES
git filter-repo --quiet --force \
    --preserve-commit-hashes \
    --replace-text "$work/replacements.txt" \
    --commit-callback "
KEPT = {$kept}
if commit.author_email not in KEPT:
    commit.author_name, commit.author_email = b'$PUBLIC_NAME', b'$PUBLIC_EMAIL'
if commit.committer_email not in KEPT:
    commit.committer_name, commit.committer_email = b'$PUBLIC_NAME', b'$PUBLIC_EMAIL'
"

echo "== Vérifications =="
unknown=$(git log --format='%ae%n%ce' | sort -u | grep -vxF -f <(printf '%s\n' "${KEPT_IDENTITIES[@]}") || true)
if [[ -n "$unknown" ]]; then
    echo "Identités non prévues dans la copie : $unknown" >&2
    exit 1
fi

git fetch --quiet "$PUBLIC_URL" main
public_head=$(git rev-parse FETCH_HEAD)
git merge-base --is-ancestor "$public_head" HEAD || {
    echo "Les commits publics ne sont pas retrouvés à l'identique (${public_head:0:8}) : abandon." >&2
    echo "Publier ainsi réécrirait l'historique public. Comprendre pourquoi avant toute chose." >&2
    exit 1
}

new_commits=$(git rev-list --count "$public_head..HEAD")
if [[ "$new_commits" == "0" ]]; then
    echo "Le dépôt public est déjà à jour."
    exit 0
fi

# Chaque nom d'utilisateur Windows restant serait une fuite : la règle ci-dessus doit tous les couvrir.
if git log -p "$public_head..HEAD" --format='%B' | grep -E '^\+|^[^-@ ]' \
    | grep -E 'Users[\\/]+[A-Za-z0-9._-]+' | grep -vE 'Users[\\/]+(<user>|eybo|runner)' >&2; then
    echo "Un chemin d'utilisateur reste dans les nouveaux commits : abandon." >&2
    exit 1
fi

gitleaks git --no-banner --redact --log-opts="$public_head..HEAD" . >&2 || {
    echo "gitleaks signale un secret possible dans les nouveaux commits : abandon." >&2
    exit 1
}

echo "Commits à ajouter : $new_commits (depuis ${public_head:0:8})"
git log -1 --format='Dernier : %h %an | %s'
git diff --shortstat "$public_head" HEAD

if $dry_run; then
    echo "Essai à blanc : rien n'a été publié."
    exit 0
fi

echo "== Publication sur $PUBLIC_URL =="
git push --quiet "$PUBLIC_URL" HEAD:main
echo "Publié : https://github.com/elie00/jelyvo/commit/$(git rev-parse HEAD)"
