#!/usr/bin/env bash
#
# One-shot dev setup for building the KlipPocket APK on Linux or macOS.
#
#   ./scripts/setup.sh            # install SDK/NDK/CMake + Python, write local.properties
#   ./scripts/setup.sh --build    # ... then build the arm64 debug APK
#
# What it does:
#   1. checks for a JDK (17+)
#   2. bootstraps the Android command-line tools if no SDK is found
#   3. installs the exact SDK packages this project pins
#   4. makes sure a Python 3.10 interpreter exists (Chaquopy buildPython)
#   5. writes local.properties (sdk.dir + chaquopy.python)
#
# Windows: use scripts/setup.ps1 (PowerShell).
# ---------------------------------------------------------------------------
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Pinned versions — keep in sync with app/build.gradle
NDK_VERSION="23.2.8568313"
CMAKE_VERSION="3.22.1"
BUILD_TOOLS="35.0.0"
PLATFORM="android-35"
PY_VERSION="3.10.14"
CMDLINE_TOOLS_VERSION="11076708"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n'  "$*"; }
die()  { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

case "$(uname -s)" in
    Linux)  OS=linux;  DEFAULT_SDK="$HOME/Android/Sdk" ;;
    Darwin) OS=mac;    DEFAULT_SDK="$HOME/Library/Android/sdk" ;;
    *) die "unsupported OS '$(uname -s)'. Use scripts/setup.ps1 on Windows." ;;
esac

# --- 1. JDK --------------------------------------------------------------
if command -v java >/dev/null 2>&1; then
    JVER="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
    [ "${JVER:-0}" -ge 17 ] 2>/dev/null || die "found Java $JVER; this project needs JDK 17+ (21 recommended). Install Temurin 21 (sdkman: 'sdk install java 21.0.5-tem', brew: 'brew install --cask temurin@21')."
    log "Java $JVER OK"
else
    die "no 'java' on PATH. Install JDK 21 (sdkman: 'sdk install java 21.0.5-tem', brew: 'brew install --cask temurin@21', apt: 'apt install openjdk-21-jdk')."
fi

# --- 2. Android SDK ----------------------------------------------------
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -z "$SDK_DIR" ] && [ -d "$DEFAULT_SDK" ] && SDK_DIR="$DEFAULT_SDK"
[ -z "$SDK_DIR" ] && [ -f local.properties ] && SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
[ -z "$SDK_DIR" ] && SDK_DIR="$DEFAULT_SDK"

SDKMANAGER=""
for c in "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" "$SDK_DIR/cmdline-tools/bin/sdkmanager" "$SDK_DIR/tools/bin/sdkmanager"; do
    [ -x "$c" ] && SDKMANAGER="$c" && break
done

if [ -z "$SDKMANAGER" ]; then
    log "No Android SDK found — bootstrapping command-line tools into $SDK_DIR"
    mkdir -p "$SDK_DIR/cmdline-tools"
    url="https://dl.google.com/android/repository/commandlinetools-${OS}-${CMDLINE_TOOLS_VERSION}_latest.zip"
    tmp="$(mktemp -d)"
    curl -fSL --retry 3 -o "$tmp/clt.zip" "$url" || die "download failed: $url"
    (cd "$tmp" && unzip -q clt.zip)
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -rf "$tmp"
    SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
fi
log "SDK dir: $SDK_DIR"
export ANDROID_HOME="$SDK_DIR" ANDROID_SDK_ROOT="$SDK_DIR"

log "Accepting SDK licenses"
yes 2>/dev/null | "$SDKMANAGER" --licenses >/dev/null || true

log "Installing SDK packages (platform-tools, $PLATFORM, build-tools $BUILD_TOOLS, ndk $NDK_VERSION, cmake $CMAKE_VERSION)"
"$SDKMANAGER" --install \
    "platform-tools" \
    "platforms;${PLATFORM}" \
    "build-tools;${BUILD_TOOLS}" \
    "ndk;${NDK_VERSION}" \
    "cmake;${CMAKE_VERSION}"

# --- 3. Python 3.10 for Chaquopy -------------------------------------
PY=""
for c in python3.10 python3 python; do
    if command -v "$c" >/dev/null 2>&1 && "$c" -c 'import sys; raise SystemExit(0 if sys.version_info[:2]==(3,10) else 1)' 2>/dev/null; then
        PY="$(command -v "$c")"; break
    fi
done

if [ -z "$PY" ] && command -v pyenv >/dev/null 2>&1; then
    log "Installing Python $PY_VERSION via pyenv"
    pyenv install -s "$PY_VERSION"
    PY="$(pyenv root)/versions/$PY_VERSION/bin/python"
fi

if [ -z "$PY" ]; then
    if [ "$OS" = mac ] && command -v brew >/dev/null 2>&1; then
        die "no Python 3.10 found. Run 'brew install python@3.10' then re-run this script, or install pyenv."
    fi
    die "no Python 3.10 found. Chaquopy needs a matching 3.10 interpreter. Install pyenv ('curl https://pyenv.run | bash') and re-run, or install python3.10 from your package manager / python.org."
fi
log "Python 3.10: $PY"

# --- 4. local.properties -------------------------------------------
{
    echo "sdk.dir=$SDK_DIR"
    echo "chaquopy.python=$PY"
} > local.properties
log "Wrote local.properties"

# --- 5. build (optional) ------------------------------------------
if [ "${1:-}" = "--build" ]; then
    log "Building arm64 debug APK"
    ./gradlew :app:assembleArm64Debug
    log "APK: app/build/outputs/apk/arm64/debug/"
else
    echo
    log "Setup done. Build with:  ./gradlew :app:assembleArm64Debug"
    log "  (or assembleArmv7Debug / assembleAmd64Debug / assembleRelease)"
fi
