#!/usr/bin/env bash
# GGym toolchain bootstrap — WSL2, no sudo.
#
# Installs, entirely under $HOME:
#   ~/tools/jdk-21            Temurin JDK 21 LTS
#   ~/Android/Sdk             Android SDK cmdline-tools + platform-tools
#                             + platforms;android-37 + build-tools;36.0.0
# and appends the env exports to ~/.bashrc (idempotent).
#
# Re-running is safe: existing pieces are skipped.
set -euo pipefail

JDK_DIR="$HOME/tools/jdk-21"
SDK_DIR="$HOME/Android/Sdk"
CMDLINE_DIR="$SDK_DIR/cmdline-tools/latest"
JDK_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
# NOTE: platform packages are minor-versioned now (android-37.0, 37.1, ...).
SDK_PLATFORM="platforms;android-37.0"
SDK_BUILD_TOOLS_LIST=("build-tools;36.0.0" "build-tools;37.0.0")  # AGP default + current
SDK_PLATFORM_TOOLS="platform-tools"

log() { printf '\n=== %s\n' "$*"; }

# ---------------------------------------------------------------- JDK
if [ ! -x "$JDK_DIR/bin/javac" ]; then
  log "Installing Temurin JDK 21 LTS -> $JDK_DIR"
  mkdir -p "$HOME/tools"
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/jdk.tar.gz" "$JDK_URL"
  tar xzf "$tmp/jdk.tar.gz" -C "$tmp"
  rm -rf "$JDK_DIR"
  mv "$tmp"/jdk-21* "$JDK_DIR"
  rm -rf "$tmp"
else
  log "JDK already present at $JDK_DIR"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ------------------------------------------------------- cmdline-tools
if [ ! -x "$CMDLINE_DIR/bin/sdkmanager" ]; then
  log "Installing Android cmdline-tools -> $CMDLINE_DIR"
  # Resolve the newest commandlinetools-linux-*_latest.zip advertised by Google.
  zip_name="$(curl -sS https://dl.google.com/android/repository/repository2-3.xml \
    | grep -o 'commandlinetools-linux-[0-9]*_latest\.zip' | sort -u \
    | sed 's/[^0-9]//g' | sort -n | tail -1)"
  zip_name="commandlinetools-linux-${zip_name}_latest.zip"
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$zip_name"
  python3 -m zipfile -e "$tmp/tools.zip" "$tmp/x"   # no unzip in this image
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$CMDLINE_DIR"
  mv "$tmp/x/cmdline-tools" "$CMDLINE_DIR"
  chmod -R +x "$CMDLINE_DIR/bin"
  rm -rf "$tmp"
else
  log "cmdline-tools already present at $CMDLINE_DIR"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

# ---------------------------------------------------------- SDK packages
log "Accepting licenses and installing SDK packages"
yes | sdkmanager --licenses > /dev/null || true
sdkmanager --install "$SDK_PLATFORM_TOOLS" "$SDK_PLATFORM" "${SDK_BUILD_TOOLS_LIST[@]}"
# python3 -m zipfile does not restore the executable bit on the cmdline-tools binaries:
chmod -R +x "$CMDLINE_DIR/bin"
sdkmanager --list_installed

# --------------------------------------------------------------- env
log "Writing env exports into ~/.bashrc (idempotent)"
marker="# >>> ggym toolchain >>>"
if ! grep -qF "$marker" "$HOME/.bashrc"; then
  cat >> "$HOME/.bashrc" <<'EOF'

# >>> ggym toolchain >>>
export JAVA_HOME="$HOME/tools/jdk-21"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
# <<< ggym toolchain <<<
EOF
else
  log "env exports already in ~/.bashrc"
fi

log "Done. Restart the shell or run: source ~/ggym/scripts/env.sh"
