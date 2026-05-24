# Reproducible JVM build environment for Synthesia-Desktop Stage 1.
# Compiles Kotlin + runs JUnit tests inside the container. Cannot produce a Windows .msi —
# jpackage is OS-bound, so run `gradlew createDistributable` on the Windows host for that.
FROM eclipse-temurin:17-jdk-jammy

ARG GRADLE_VERSION=8.7

ENV GRADLE_HOME=/opt/gradle \
    DEBIAN_FRONTEND=noninteractive
ENV PATH=$GRADLE_HOME/bin:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

RUN cd /opt \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o gradle.zip \
    && unzip -q gradle.zip \
    && mv "gradle-${GRADLE_VERSION}" gradle \
    && rm gradle.zip

WORKDIR /workspace

CMD ["gradle", "--no-daemon", "build"]
