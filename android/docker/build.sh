#!/usr/bin/env bash
#
# Roda o build do app Android num container efemero.
#
# Regra do ambiente: nada de JDK/SDK/Gradle instalado no SO do host. Os caches
# (Gradle e Android) ficam em android/.build-cache/, dentro do repo — nunca em
# ~/.gradle ou ~/.android.
#
#   ./docker/build.sh                    # assembleRelease (padrao)
#   ./docker/build.sh assembleDebug
#   ./docker/build.sh test
#   ./docker/build.sh clean assembleRelease --stacktrace
#
set -euo pipefail

ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_TAG="${DIALOGLITE_BUILD_IMAGE:-dialoglite-android-build:jdk21-sdk36}"
CACHE_DIR="${ANDROID_DIR}/.build-cache"

if [[ $# -eq 0 ]]; then
  set -- assembleRelease
fi

if ! docker image inspect "${IMAGE_TAG}" >/dev/null 2>&1; then
  echo ">> construindo imagem de build ${IMAGE_TAG} (so na primeira vez)"
  docker build -t "${IMAGE_TAG}" "${ANDROID_DIR}/docker"
fi

mkdir -p "${CACHE_DIR}/gradle" "${CACHE_DIR}/android" "${CACHE_DIR}/home"

echo ">> gradle $*"
exec docker run --rm \
  --user "$(id -u):$(id -g)" \
  -v "${ANDROID_DIR}:/workspace" \
  -w /workspace \
  -e HOME=/workspace/.build-cache/home \
  -e GRADLE_USER_HOME=/workspace/.build-cache/gradle \
  -e ANDROID_USER_HOME=/workspace/.build-cache/android \
  -e DIALOGLITE_KEYSTORE="${DIALOGLITE_KEYSTORE:-}" \
  -e DIALOGLITE_KEYSTORE_PASSWORD="${DIALOGLITE_KEYSTORE_PASSWORD:-}" \
  -e DIALOGLITE_KEY_ALIAS="${DIALOGLITE_KEY_ALIAS:-}" \
  -e DIALOGLITE_KEY_PASSWORD="${DIALOGLITE_KEY_PASSWORD:-}" \
  "${IMAGE_TAG}" \
  ./gradlew --no-daemon "$@"
