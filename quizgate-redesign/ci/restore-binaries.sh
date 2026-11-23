#!/usr/bin/env bash
# Restores base64-encoded binary assets (*.b64, and split parts *.b64.aa/.ab/...) to their real paths.
cd "$(dirname "$0")/.."
find . -name '*.b64' | while read -r f; do
  base64 -d "$f" > "${f%.b64}"
done
find . -name '*.b64.aa' | while read -r f; do
  b="${f%.aa}"
  cat "$b".* | base64 -d > "${b%.b64}"
done
