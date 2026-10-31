#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../frontend"
npm run generate:api
npm run build
