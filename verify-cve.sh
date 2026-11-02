#!/usr/bin/env bash
set -euo pipefail
IMAGE="reops-clean-to-enriched"

echo "== gradle bootJar =="
./gradlew bootJar

echo "== docker build =="
docker build --pull --no-cache -t "$IMAGE" .

echo "== grype scan =="
grype "$IMAGE" -o table
