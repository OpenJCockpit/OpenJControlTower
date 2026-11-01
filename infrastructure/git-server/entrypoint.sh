#!/bin/sh
# Creates the bare repositories named in GIT_SERVER_REPOSITORIES (each with an initial commit
# on main) and one login from GIT_SERVER_USER / GIT_SERVER_PASSWORD, then serves them over HTTP.
set -eu
: "${GIT_SERVER_USER:?}" "${GIT_SERVER_PASSWORD:?}"
printf '%s:%s\n' "$GIT_SERVER_USER" "$GIT_SERVER_PASSWORD" > /run/lighttpd/users
export HOME=/tmp
for name in ${GIT_SERVER_REPOSITORIES:-example}; do
  bare="/srv/git/$name.git"
  [ -d "$bare" ] && continue
  git init -q --bare -b main "$bare"
  git -C "$bare" config http.receivepack true
  seed=$(mktemp -d)
  git -C "$seed" init -q -b main
  printf '# %s\n\nSeeded by the OpenJControlTower development Git server.\n' "$name" > "$seed/README.md"
  git -C "$seed" add README.md
  git -C "$seed" -c user.name=git-server -c user.email=git-server@localhost commit -q -m "Initial commit"
  git -C "$seed" push -q "$bare" main
  rm -rf "$seed"
done
exec lighttpd -D -f /etc/lighttpd/lighttpd.conf
