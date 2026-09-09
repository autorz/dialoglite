# Ambiente de build Android — JDK + Android SDK, sem tocar o SO do host.
#
# O SDK vive DENTRO da imagem (/opt/android-sdk). O que e montado do host e
# apenas o codigo e os caches (GRADLE_USER_HOME / ANDROID_USER_HOME), sempre
# para dentro do diretorio de trabalho — nunca ~/.gradle ou ~/.android.
FROM eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS_VERSION=11076708
ARG BUILD_TOOLS_VERSION=36.0.0
ARG PLATFORM_VERSION=36

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"; \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"; \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools; \
    mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest"; \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools

ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

RUN set -eux; \
    yes | sdkmanager --licenses > /dev/null; \
    sdkmanager --install \
      "platform-tools" \
      "platforms;android-${PLATFORM_VERSION}" \
      "build-tools;${BUILD_TOOLS_VERSION}" > /dev/null; \
    chmod -R a+rX "${ANDROID_SDK_ROOT}"

# O container roda com o uid do host (`--user`), entao HOME precisa ser
# gravavel; o build.sh aponta HOME/GRADLE_USER_HOME/ANDROID_USER_HOME pra
# dentro do volume montado.
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

WORKDIR /workspace
