# Reproducible Android build environment for Synthesia-Clone Stage 1.
# Output: app/build/outputs/apk/debug/app-debug.apk (in the bind-mounted workspace).
FROM eclipse-temurin:17-jdk-jammy

ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_COMPILE_SDK=34
ARG ANDROID_BUILD_TOOLS=34.0.0
ARG GRADLE_VERSION=8.7

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle \
    DEBIAN_FRONTEND=noninteractive
ENV PATH=$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

# Android SDK command-line tools + platform + build-tools.
RUN mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" -o cmdline-tools.zip \
    && unzip -q cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_COMPILE_SDK}" \
        "build-tools;${ANDROID_BUILD_TOOLS}"

# Gradle (independent of any host gradlew).
RUN cd /opt \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o gradle.zip \
    && unzip -q gradle.zip \
    && mv "gradle-${GRADLE_VERSION}" gradle \
    && rm gradle.zip

WORKDIR /workspace

# Source is bind-mounted at /workspace by docker compose. Default command builds the debug APK.
CMD ["gradle", "--no-daemon", ":app:assembleDebug"]
