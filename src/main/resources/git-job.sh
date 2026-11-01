# Runs inside a disposable, isolated container (Docker or a Kubernetes Job): clone the base branch,
# create the spec branch, commit every change separately and push. The container is removed afterwards.
#
# Inputs (environment): OJCT_REPOSITORY, OJCT_BASE_BRANCH, OJCT_BRANCH, OJCT_CHANGES and, per change i,
# OJCT_CHANGE_<i>_PATH, _MODE (create|append), _CONTENT, _MESSAGE and _HEADER (append only; base64).
# GIT_USERNAME / GIT_PASSWORD are optional; they never appear in a URL, argument or file.
# Output: the last line is OJCT_COMMIT=<sha>, or OJCT_ERROR=<reason> on a known failure.
set -eu
umask 077
work="$HOME"
cd "$work"
if [ -n "${GIT_PASSWORD:-}" ]; then
  git config --global credential.helper '!f() { test "$1" = get || exit 0; echo "username=${GIT_USERNAME}"; echo "password=${GIT_PASSWORD}"; }; f'
fi
git config --global advice.detachedHead false

git clone --quiet --depth 1 --single-branch --branch "$OJCT_BASE_BRANCH" -- "$OJCT_REPOSITORY" "$work/repo"
cd "$work/repo"
if git ls-remote --exit-code --heads origin "$OJCT_BRANCH" >/dev/null 2>&1; then
  echo "OJCT_ERROR=Branch $OJCT_BRANCH already exists on the remote"
  exit 3
fi
git checkout --quiet -b "$OJCT_BRANCH"

i=1
while [ "$i" -le "$OJCT_CHANGES" ]; do
  path=$(printenv "OJCT_CHANGE_${i}_PATH")
  printenv "OJCT_CHANGE_${i}_CONTENT" | base64 -d > "$work/content"
  printenv "OJCT_CHANGE_${i}_MESSAGE" | base64 -d > "$work/message"
  mkdir -p "$(dirname "$path")"
  case "$(printenv "OJCT_CHANGE_${i}_MODE")" in
    create)
      if [ -e "$path" ]; then echo "OJCT_ERROR=$path already exists in the repository"; exit 4; fi
      cp "$work/content" "$path" ;;
    append)
      [ -e "$path" ] || printenv "OJCT_CHANGE_${i}_HEADER" | base64 -d > "$path"
      cat "$work/content" >> "$path" ;;
    *) echo "OJCT_ERROR=Unknown change mode"; exit 5 ;;
  esac
  git add -- "$path"
  git commit --quiet -F "$work/message"
  i=$((i + 1))
done

git push --quiet origin "HEAD:refs/heads/$OJCT_BRANCH"
echo "OJCT_COMMIT=$(git rev-parse HEAD)"
