#!/usr/bin/env bash
#
# Convenience wrapper: build the Elegoo Neptune 3 Pro MCU firmware.
#
# All the real logic (pinned Klipper tag, pinned ARM toolchain, brick-safety
# asserts, no obfuscation, reproducibility check) lives in build_firmware.sh and
# in the "#!" directives at the top of firmware/configs/elegoo-neptune3-pro.config.
#
#   ./scripts/build_neptune3pro_firmware.sh
#   -> firmware/output/ZNP_ROBIN_NANO.bin   (plain klipper.bin, just renamed)
#
# Flashing + safety notes: docs/build-firmware.md
# ---------------------------------------------------------------------------
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/build_firmware.sh" elegoo-neptune3-pro "$@"
