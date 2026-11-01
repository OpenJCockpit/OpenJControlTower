# Development and test only: a small Git server over HTTP with basic authentication,
# so the stack can clone and push without an external Git host. Never expose it publicly.
FROM alpine:3.22
RUN apk add --no-cache git git-daemon lighttpd lighttpd-mod_auth \
 && addgroup -S -g 10002 git && adduser -S -D -H -u 10002 -G git git \
 && mkdir -p /srv/git /run/lighttpd && chown git:git /srv/git /run/lighttpd
COPY lighttpd.conf /etc/lighttpd/lighttpd.conf
COPY entrypoint.sh /usr/local/bin/git-server
USER 10002:10002
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/git-server"]
