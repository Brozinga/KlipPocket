#!/usr/bin/env bash
#
# Container entrypoint of the KlipPocket firmware image.
#
#   fwtool.sh menuconfig <klipper|kalico> <profile>   real `make menuconfig`
#   fwtool.sh build      <klipper|kalico> <profile>   compile + write manifest
#   fwtool.sh validate   <klipper|kalico> <profile> [--deep]
#   fwtool.sh devices                                 serial ports + USB boot devices
#   fwtool.sh flash     <klipper|kalico> <profile> <device> <usb|uart|dfu|rp2040>
#   fwtool.sh verify    <klipper|kalico> <profile> <serial-port|auto> [seconds]
#   fwtool.sh info                                    engine versions in the image
#   fwtool.sh shell                                   bash
#   fwtool.sh <board> [output-name]                   legacy: build_firmware.sh
#
# <profile> is /configs/<profile>.config. Output goes to /out.
# Set HOST_UID / HOST_GID so the files written to the mounted folders belong to
# the invoking user on Linux/macOS.
set -euo pipefail

CONFIGS="${FW_CONFIGS_DIR:-/configs}"
OUT="${FW_OUTPUT_DIR:-/out}"
CHECK="${FW_CHECK_TOOL:-/opt/fwcheck.py}"

log() { printf '\033[1;34m==>\033[0m %s\n' "$1"; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$1" >&2; exit 1; }

# shellcheck disable=SC1091
[ -f /opt/engines.env ] && . /opt/engines.env

engine_dir() {
    case "$1" in
        klipper) echo /opt/klipper ;;
        kalico)  echo /opt/kalico ;;
        *) die "unknown engine '$1' (use klipper or kalico)" ;;
    esac
}
engine_tag() {
    case "$1" in
        klipper) echo "${KLIPPER_TAG:-?}" ;;
        kalico)  echo "${KALICO_TAG:-?}" ;;
    esac
}
fix_owner() {
    [ -n "${HOST_UID:-}" ] || return 0
    chown -R "$HOST_UID:${HOST_GID:-$HOST_UID}" "$CONFIGS" "$OUT" 2>/dev/null || true
}
trap fix_owner EXIT

need_profile() {
    [ -n "${1:-}" ] && [ -n "${2:-}" ] || die "usage: $0 $CMD <klipper|kalico> <profile>"
    ENGINE="$1"; PROFILE="$2"
    SRC="$(engine_dir "$ENGINE")"
    TAG="$(engine_tag "$ENGINE")"
    CFG="$CONFIGS/$PROFILE.config"
    BASE="$PROFILE-$ENGINE"
}

CMD="${1:---help}"; [ $# -gt 0 ] && shift || true
case "$CMD" in
    menuconfig)
        need_profile "${1:-}" "${2:-}"
        [ -t 0 ] && [ -t 1 ] || die "menuconfig needs an interactive terminal (docker run -it)"
        mkdir -p "$CONFIGS"
        # menuconfig rewrites the file and drops our "#!" header: keep it.
        hdr=""
        [ -f "$CFG" ] && hdr="$(grep '^#!' "$CFG" || true)"
        log "$ENGINE $TAG — Firmware Configuration ($PROFILE.config)"
        cd "$SRC"
        make menuconfig KCONFIG_CONFIG="$CFG" || true
        # The extras Kconfig the Makefile generated lives only in this container.
        if [ -f "$CFG" ] && [ -n "$hdr" ]; then
            { printf '%s\n' "$hdr"; cat "$CFG"; } > "$CFG.tmp" && mv "$CFG.tmp" "$CFG"
        fi
        [ -f "$CFG" ] && log "Saved: firmware/configs/$PROFILE.config" || log "No config saved."
        ;;
    build)
        need_profile "${1:-}" "${2:-}"
        [ -f "$CFG" ] || die "no configuration at firmware/configs/$PROFILE.config — run Firmware Configuration first"
        FW_KLIPPER_SRC="$SRC" FW_ENGINE="$ENGINE" FW_ENGINE_TAG="$TAG" FW_OUT_BASE="$BASE" \
            /opt/build_firmware.sh "$CFG"
        ;;
    validate)
        need_profile "${1:-}" "${2:-}"
        shift 2 || true
        DEEP=0; [ "${1:-}" = "--deep" ] && DEEP=1
        python3 "$CHECK" validate --out-dir "$OUT" --base "$BASE" --config "$CFG" \
            --engine "$ENGINE" --tag "$TAG" || RC=$?
        RC="${RC:-0}"
        if [ "$DEEP" = 1 ] && [ "$RC" -eq 0 ]; then
            log "Deep check: rebuilding in a clean tree and comparing hashes..."
            tmp="$(mktemp -d)"
            FW_OUTPUT_DIR="$tmp" FW_KLIPPER_SRC="$SRC" FW_ENGINE="$ENGINE" FW_ENGINE_TAG="$TAG" \
                FW_OUT_BASE="$BASE" /opt/build_firmware.sh "$CFG" >/dev/null 2>&1 \
                || die "deep check: the rebuild failed"
            python3 "$CHECK" compare --a "$OUT" --b "$tmp" --base "$BASE" || RC=$?
            rm -rf "$tmp"
        fi
        exit "$RC"
        ;;
    devices)
        # TYPE<TAB>VALUE<TAB>DESCRIPTION
        #   usb     serial port of a USB-CDC device (Klipper/Katapult USB, Arduino...)
        #   uart    serial port of a USB-serial adapter (CH340, FTDI, CP210x...)
        #   dfu     STM32 (and clones) in DFU mode               VALUE = vid:pid
        #   rp2040  Raspberry Pi RP2040/RP2350 in BOOTSEL mode    VALUE = vid:pid
        #   info    any other USB device (listed for reference only)
        usb_info() {   # <sysfs tty path>: sets U_VID U_PID U_PRODUCT by walking up to the USB device
            d="$(readlink -f "$1/device" 2>/dev/null)"; U_VID=""; U_PID=""; U_PRODUCT=""
            while [ -n "$d" ] && [ "$d" != / ]; do
                if [ -f "$d/idVendor" ]; then
                    U_VID="$(cat "$d/idVendor")"; U_PID="$(cat "$d/idProduct" 2>/dev/null)"
                    U_PRODUCT="$(cat "$d/product" 2>/dev/null)"; return
                fi
                d="$(dirname "$d")"
            done
        }
        for t in /sys/class/tty/ttyUSB* /sys/class/tty/ttyACM*; do
            [ -e "$t" ] || continue
            name="$(basename "$t")"; node="/dev/$name"; [ -e "$node" ] || continue
            drv="$(basename "$(readlink -f "$t/device/driver" 2>/dev/null)")"
            usb_info "$t"
            path="$node"
            for b in /dev/serial/by-id/*; do
                [ -e "$b" ] && [ "$(readlink -f "$b")" = "$node" ] && { path="$b"; break; }
            done
            kind=uart; [ "$drv" = cdc_acm ] && kind=usb
            printf '%s\t%s\t%s %s (%s, %s)\n' "$kind" "$path" "${U_VID:-????}:${U_PID:-????}" "$U_PRODUCT" "$name" "${drv:-?}"
        done
        lsusb 2>/dev/null | while IFS= read -r l; do
            id="$(printf '%s' "$l" | sed -n 's/.*ID \([0-9a-fA-F]\{4\}:[0-9a-fA-F]\{4\}\) *\(.*\)/\1/p')"
            nm="$(printf '%s' "$l" | sed -n 's/.*ID [0-9a-fA-F:]* *\(.*\)/\1/p')"
            case "$id" in
                0483:df11|2e3c:df11) printf 'dfu\t%s\t%s\n' "$id" "$nm" ;;
                2e8a:0003) printf 'rp2040\t%s\tRP2040 BOOTSEL - %s\n' "$id" "$nm" ;;
                2e8a:000f) printf 'rp2040\t%s\tRP2350 BOOTSEL - %s\n' "$id" "$nm" ;;
                1d6b:*)    : ;;   # root hubs
                *)         printf 'info\t%s\t%s\n' "$id" "$nm" ;;
            esac
        done
        ;;
    flash)
        need_profile "${1:-}" "${2:-}"
        DEVICE="${3:-}"; MODE="${4:-usb}"
        [ -n "$DEVICE" ] || die "usage: $0 flash <engine> <profile> <device> [usb|uart|dfu|rp2040]"
        # avrdude would wait forever on a path that does not exist
        case "$DEVICE" in /*) [ -e "$DEVICE" ] || die "device $DEVICE not found (is the board plugged in and visible to Docker?)" ;; esac
        log "1/3 Checking the validated build..."
        python3 "$CHECK" validate --out-dir "$OUT" --base "$BASE" --config "$CFG" \
            --engine "$ENGINE" --tag "$TAG" || die "validation failed — not flashing"
        USED="$OUT/$BASE.config.used"
        has() { grep -q "$1" "$USED" 2>/dev/null; }
        has '^CONFIG_MACH_LINUX=y' && die "the Linux-process MCU is installed on the host itself, not over USB"
        # the mode must fit the chip this firmware was built for: a wrong image can brick a board
        METHOD=flash
        case "$MODE" in
            usb)   ;;
            uart)  has '^CONFIG_MACH_STM32=y' && METHOD=serialflash ;;
            dfu)   has '^CONFIG_MACH_STM32=y' || die "DFU mode needs an STM32 firmware, but this one is for $(sed -n 's/^CONFIG_MCU="\(.*\)"/\1/p' "$USED")" ;;
            rp2040)
                mcu="$(sed -n 's/^CONFIG_MCU="\(.*\)"/\1/p' "$USED")"
                case "$mcu:$DEVICE" in
                    rp2040:2e8a:0003|rp2350:2e8a:000f) ;;
                    rp2040:*|rp2350:*) die "this firmware is for $mcu but the device $DEVICE is a different chip" ;;
                    *) die "RP2040 mode needs an RP2040/RP2350 firmware, but this one is for $mcu" ;;
                esac ;;
            *) die "unknown mode '$MODE' (use usb, uart, dfu or rp2040)" ;;
        esac
        log "2/3 Rebuilding and comparing with the validated image..."
        export PATH="${FW_TOOLCHAIN_BIN:-/opt/none}:$PATH"   # the same pinned ARM compiler as the build
        cd "$SRC"
        cleanup_flash() { make clean >/dev/null 2>&1 || true; rm -f .config .config.old; }
        trap 'cleanup_flash; fix_owner' EXIT
        grep -v '^#!' "$CFG" > .config
        make olddefconfig >/dev/null
        make -j"$(nproc 2>/dev/null || echo 4)" >/dev/null || die "the rebuild failed"
        python3 "$CHECK" matchbuild --out-dir "$OUT" --base "$BASE" --build-out out \
            || die "the sources/toolchain changed since the build — rebuild first (Make Firmware)"
        if ! make -n "$METHOD" FLASH_DEVICE="$DEVICE" >/dev/null 2>&1; then
            die "this MCU has no '$METHOD' target: flash it another way (e.g. copy the image to the SD card: firmware/output/)"
        fi
        log "3/3 Flashing to $DEVICE (mode $MODE, make $METHOD)..."
        rc=0
        timeout 240 make "$METHOD" FLASH_DEVICE="$DEVICE" NOSUDO=1 V=1 || rc=$?
        [ "$rc" -ne 124 ] || die "flashing timed out after 240 s — is the board in its bootloader mode?"
        [ "$rc" -eq 0 ] || die "the flash tool reported an error (see above) — the board was NOT updated, or only partly"
        log "Flash command finished. Run Verify to confirm what the board is running."
        ;;
    verify)
        need_profile "${1:-}" "${2:-}"
        PORT="${3:-}"; WAIT="${4:-20}"
        [ -n "$PORT" ] || die "usage: $0 verify <engine> <profile> <serial-port|auto> [seconds]"
        probe() { python3 "$CHECK" probe --port "$1" --dict "$OUT/$BASE.dict" \
                      --msgproto "$SRC/klippy/msgproto.py" --timeout "${2:-$WAIT}"; }
        if [ "$PORT" != auto ]; then probe "$PORT"; exit $?; fi
        # auto: wait for a serial port to show up (the board restarts after a flash), then ask each one
        end=$((SECONDS + WAIT)); ports=""
        printf 'Waiting for the board to come back (up to %ss)...\n' "$WAIT"
        while [ "$SECONDS" -lt "$end" ]; do
            ports="$("$0" devices | awk -F'\t' '$1=="usb"||$1=="uart"{print $2}')"
            [ -n "$ports" ] && break
            sleep 1
        done
        [ -n "$ports" ] || { printf '\033[31m  [FAIL]\033[0m no serial port appeared\n'; exit 1; }
        rc=1
        for p in $ports; do probe "$p" 10 && { rc=0; break; }; done
        exit "$rc"
        ;;
    info)
        printf 'Klipper %s\nKalico  %s\nARM toolchain %s\n' \
            "${KLIPPER_TAG:-?}" "${KALICO_TAG:-?}" "${TOOLCHAIN_VERSION:-?}"
        ;;
    shell)
        exec bash
        ;;
    --help|-h|help)
        exec /opt/build_firmware.sh --help
        ;;
    *)
        # legacy usage: <board | path/to/.config> [output-name] [klipper-tag]
        FW_KLIPPER_SRC=/opt/klipper FW_ENGINE=klipper FW_ENGINE_TAG="${KLIPPER_TAG:-}" \
            /opt/build_firmware.sh "$CMD" "$@"
        ;;
esac
