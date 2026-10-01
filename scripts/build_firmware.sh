#!/usr/bin/env bash
#
# Generic MCU-firmware builder for KlipPocket.
#
# Builds a plain `klipper.bin` for ANY board from a Klipper `.config`, using a
# pinned upstream Klipper tag and a pinned ARM toolchain, with no post-processing
# ("obfuscation") step. Works on a normal Linux PC or inside the firmware/ Docker
# image.
#
#   ./scripts/build_firmware.sh <board | path/to/.config> [output-name] [klipper-tag]
#
#   <board>        name of a file in firmware/configs/  (e.g. "elegoo-neptune3-pro"
#                  -> firmware/configs/elegoo-neptune3-pro.config), or a full path
#                  to any Klipper .config you saved with `make menuconfig`.
#   [output-name]  filename to write into firmware/output/ (default: the config's
#                  "#! flash_filename:" directive, else "klipper.bin").
#   [klipper-tag]  overrides the config's "#! klipper_tag:" (default: v0.13.0).
#
# Config directives (optional header lines in firmware/configs/*.config):
#   #! board_name:      free text, shown in the log
#   #! flash_filename:  the exact name the board's SD bootloader looks for
#   #! klipper_tag:     Klipper git tag to build from
#   #! assert: <line>   a .config line that MUST survive `make olddefconfig`
#                       (repeatable — these are your brick-safety guards)
#   #! expect_sha256:   if set, the build fails unless the output matches
#   #! allow_no_bootloader: yes   suppress the "would overwrite bootloader" abort
#
# See docs/build-firmware.md for the full walkthrough and safety notes.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIGS_DIR="${FW_CONFIGS_DIR:-$REPO_ROOT/firmware/configs}"
OUTPUT_DIR="${FW_OUTPUT_DIR:-$REPO_ROOT/firmware/output}"
TOOLCHAIN_DIR="$REPO_ROOT/firmware/toolchain"
BUILD_DIR="${FW_BUILD_DIR:-$REPO_ROOT/firmware/.klipper-build}"

DEFAULT_KLIPPER_TAG="v0.13.0"
KLIPPER_REPO="https://github.com/Klipper3d/klipper.git"
TOOLCHAIN_VERSION="${FW_TOOLCHAIN_VERSION:-10.3-2021.07}"
case "$(uname -s)" in
    Darwin) _TC_HOST="mac" ;;
    Linux)  _TC_HOST="x86_64-linux" ;;
    *)      _TC_HOST="x86_64-linux" ;;   # Git Bash / other: try the Linux tarball
esac
TOOLCHAIN_URL="https://developer.arm.com/-/media/Files/downloads/gnu-rm/${TOOLCHAIN_VERSION}/gcc-arm-none-eabi-${TOOLCHAIN_VERSION}-${_TC_HOST}.tar.bz2"

# --- portable helpers (Linux + macOS + Git Bash) ------------------------
ncpu()  { nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4; }
fsize() { wc -c < "$1" | tr -d ' '; }

log()  { printf '\033[1;34m==>\033[0m %s\n' "$1"; }
warn() { printf '\033[1;33m!!\033[0m %s\n'  "$1"; }
die()  { printf '\033[1;31mERROR:\033[0m %s\n' "$1" >&2; exit 1; }

[ $# -ge 1 ] || die "usage: $0 <board | path/to/.config> [output-name] [klipper-tag]"

# --- resolve the config ----------------------------------------------------
ARG="$1"
if [ -f "$ARG" ]; then
    CONFIG_SRC="$(cd "$(dirname "$ARG")" && pwd)/$(basename "$ARG")"
elif [ -f "$CONFIGS_DIR/$ARG.config" ]; then
    CONFIG_SRC="$CONFIGS_DIR/$ARG.config"
elif [ -f "$CONFIGS_DIR/$ARG" ]; then
    CONFIG_SRC="$CONFIGS_DIR/$ARG"
else
    echo "Available boards in firmware/configs/:" >&2
    (cd "$CONFIGS_DIR" 2>/dev/null && ls -1 *.config 2>/dev/null | sed 's/\.config$//; s/^/  /') >&2 || true
    die "no config found for '$ARG'"
fi

# --- parse #! directives (portable sed: no \s / no mapfile) --------------
directive() { sed -n "s/^#![[:space:]]*$1:[[:space:]]*//p" "$CONFIG_SRC" | head -1; }
BOARD_NAME="$(directive board_name)";       BOARD_NAME="${BOARD_NAME:-$(basename "$CONFIG_SRC" .config)}"
DIR_FLASH_NAME="$(directive flash_filename)"
DIR_TAG="$(directive klipper_tag)"
EXPECT_SHA="$(directive expect_sha256)"
ALLOW_NO_BL="$(directive allow_no_bootloader)"
ASSERTS=()
while IFS= read -r _a; do ASSERTS+=("$_a"); done < <(sed -n 's/^#![[:space:]]*assert:[[:space:]]*//p' "$CONFIG_SRC")

KLIPPER_TAG="${3:-${DIR_TAG:-$DEFAULT_KLIPPER_TAG}}"
OUT_NAME="${2:-${DIR_FLASH_NAME:-klipper.bin}}"

log "Board:        $BOARD_NAME"
log "Config:       ${CONFIG_SRC#$REPO_ROOT/}"
log "Klipper tag:  $KLIPPER_TAG"
log "Output name:  $OUT_NAME"

for tool in make python3 git; do command -v "$tool" >/dev/null || die "missing required tool: $tool"; done

# --- toolchain -----------------------------------------------------------
if [ -n "${FW_TOOLCHAIN_BIN:-}" ] && [ -x "$FW_TOOLCHAIN_BIN/arm-none-eabi-gcc" ]; then
    TOOLCHAIN_BIN="$FW_TOOLCHAIN_BIN"                       # Docker / caller-provided
else
    TOOLCHAIN_BIN="$TOOLCHAIN_DIR/gcc-arm-none-eabi-${TOOLCHAIN_VERSION}/bin"
    if [ ! -x "$TOOLCHAIN_BIN/arm-none-eabi-gcc" ]; then
        log "Downloading ARM toolchain $TOOLCHAIN_VERSION (~150 MB, first run only)..."
        mkdir -p "$TOOLCHAIN_DIR"; tmp="$(mktemp)"
        curl -fSL --max-time 600 -o "$tmp" "$TOOLCHAIN_URL" || { rm -f "$tmp"; die "toolchain download failed"; }
        tar xjf "$tmp" -C "$TOOLCHAIN_DIR"; rm -f "$tmp"
    fi
fi
[ -x "$TOOLCHAIN_BIN/arm-none-eabi-gcc" ] || die "toolchain not found at $TOOLCHAIN_BIN"
export PATH="$TOOLCHAIN_BIN:$PATH"
log "Compiler:     $("$TOOLCHAIN_BIN/arm-none-eabi-gcc" --version | head -1)"

# --- Klipper source ----------------------------------------------------
if [ -n "${FW_KLIPPER_SRC:-}" ]; then
    BUILD_DIR="$FW_KLIPPER_SRC"                             # Docker: baked checkout
    log "Klipper source (provided): $BUILD_DIR"
elif [ -d "$BUILD_DIR/.git" ] && [ "$(git -C "$BUILD_DIR" describe --tags --always 2>/dev/null)" = "$KLIPPER_TAG" ]; then
    log "Klipper $KLIPPER_TAG already checked out."
else
    log "Cloning Klipper $KLIPPER_TAG..."
    rm -rf "$BUILD_DIR"
    git clone --depth 1 --branch "$KLIPPER_TAG" "$KLIPPER_REPO" "$BUILD_DIR" >/dev/null 2>&1 \
        || die "git clone of $KLIPPER_REPO ($KLIPPER_TAG) failed"
fi
GOT="$(git -C "$BUILD_DIR" describe --tags --always 2>/dev/null || echo '?')"
case "$GOT" in "$KLIPPER_TAG"|"$KLIPPER_TAG"-*) : ;; *) warn "checkout reports '$GOT', wanted '$KLIPPER_TAG'";; esac

# --- configure + assert ------------------------------------------------
cd "$BUILD_DIR"
cleanup() { cd "$BUILD_DIR" 2>/dev/null && { make clean >/dev/null 2>&1 || true; rm -f .config .config.old; }; }
trap cleanup EXIT

grep -v '^#!' "$CONFIG_SRC" > .config
run_asserts() {
    local phase="$1"
    for a in "${ASSERTS[@]}"; do
        [ -z "$a" ] && continue
        grep -qxF "$a" .config || die "$phase: required line missing from .config: '$a' — stopping (brick-safety guard)."
    done
}
run_asserts "before olddefconfig"
log "Reconciling with Klipper $KLIPPER_TAG Kconfig (make olddefconfig)..."
make olddefconfig >/dev/null
run_asserts "after olddefconfig"

# generic bootloader-safety check: refuse to emit an image linked at flash start
app_addr="$(sed -n 's/^CONFIG_FLASH_APPLICATION_ADDRESS=//p' .config)"
boot_addr="$(sed -n 's/^CONFIG_FLASH_BOOT_ADDRESS=//p' .config)"
if [ -z "$ALLOW_NO_BL" ] && [ -n "$app_addr" ] && [ -n "$boot_addr" ] && [ "$app_addr" = "$boot_addr" ]; then
    die "CONFIG_FLASH_APPLICATION_ADDRESS == CONFIG_FLASH_BOOT_ADDRESS ($app_addr): this image would sit on top of the bootloader. If your board genuinely has no bootloader, add '#! allow_no_bootloader: yes' to the config."
fi

# --- build ------------------------------------------------------------
log "Compiling..."
make clean >/dev/null
make -j"$(ncpu)"
[ -f out/klipper.bin ] || die "build finished but out/klipper.bin was not produced"

fw_size=$(fsize out/klipper.bin)
flash_size_hex="$(sed -n 's/^CONFIG_FLASH_SIZE=//p' .config)"
if [ -n "$flash_size_hex" ] && [ -n "$app_addr" ] && [ -n "$boot_addr" ]; then
    app_area=$(( flash_size_hex - (app_addr - boot_addr) ))
    [ "$fw_size" -le "$app_area" ] || die "firmware ($fw_size B) exceeds the application area ($app_area B) — DO NOT flash."
    log "Size OK: $fw_size B (application area $app_area B)."
else
    log "Size: $fw_size B (could not compute application area — no CONFIG_FLASH_SIZE assert)."
fi

# --- package (plain binary, renamed — NO obfuscation) ----------------
mkdir -p "$OUTPUT_DIR"
final="$OUTPUT_DIR/$OUT_NAME"
cp out/klipper.bin "$final"
sha="$(sha256sum "$final" | cut -d' ' -f1)"
ver="$(cat out/*.version 2>/dev/null || echo "$KLIPPER_TAG")"

echo
log "Done: ${final}"
echo "    Klipper $ver | $fw_size bytes | sha256 $sha"
echo "    Plain klipper.bin — flash this file as-is (no update_mks_robin.py / no obfuscation)."

if [ -n "$EXPECT_SHA" ]; then
    if [ "$sha" = "$EXPECT_SHA" ]; then
        log "Reproducibility check PASSED (matches #! expect_sha256)."
    else
        die "Reproducibility check FAILED: got $sha, expected $EXPECT_SHA."
    fi
fi
