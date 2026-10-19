# KlipPocket — MCU firmware builder
#
# Reproducible firmware for Klipper AND Kalico: pinned ARM toolchain, AVR
# toolchain, and a pinned checkout of each engine (its Kconfig menu, kconfiglib
# and build scripts). See docs/build-firmware.md.
#
# Build context is the REPO ROOT (needs scripts/build_firmware.sh):
#   docker build -t klippocket-firmware -f firmware/Dockerfile .
#
# Easiest way to use it: scripts/firmware_menu.sh (Linux/macOS/WSL/Git Bash) or
# scripts/firmware_menu.ps1 (Windows PowerShell). Or directly:
#   docker run --rm -v "$PWD/firmware/configs:/configs:ro" -v "$PWD/firmware/output:/out" \
#       klippocket-firmware elegoo-neptune3-pro
FROM debian:bookworm-slim

ARG TOOLCHAIN_VERSION=10.3-2021.07
ARG KLIPPER_TAG=v0.13.0
ARG KALICO_TAG=v2026.10.00

# build-essential: host gcc (the "Linux process" MCU) + make.
# gcc-avr/avr-libc/binutils-avr: AVR boards (atmega2560, atmega1284p, ...).
# avrdude/dfu-util/stm32flash/libusb: the tools `make flash` calls to write the board
# over USB; usbutils: lsusb; python3-serial: reading back what the board runs.
# libnewlib/gcc/binutils-arm-none-eabi: fallback ARM toolchain (the pinned one
# below comes first on PATH). ncurses-term: terminfo for the menuconfig screen.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential make python3 python-is-python3 git \
        wget ca-certificates bzip2 xz-utils libncursesw6 ncurses-term \
        gcc-avr binutils-avr avr-libc \
        avrdude dfu-util stm32flash usbutils python3-serial libusb-1.0-0-dev pkg-config \
        gcc-arm-none-eabi binutils-arm-none-eabi libnewlib-arm-none-eabi \
    && rm -rf /var/lib/apt/lists/*

# Pinned ARM toolchain — the exact compiler used for the verified vendor images.
RUN case "$(uname -m)" in aarch64|arm64) TC_HOST=aarch64-linux ;; *) TC_HOST=x86_64-linux ;; esac \
    && wget -qO /tmp/tc.tar.bz2 \
        "https://developer.arm.com/-/media/Files/downloads/gnu-rm/${TOOLCHAIN_VERSION}/gcc-arm-none-eabi-${TOOLCHAIN_VERSION}-${TC_HOST}.tar.bz2" \
    && mkdir -p /opt && tar xjf /tmp/tc.tar.bz2 -C /opt && rm /tmp/tc.tar.bz2

# Pinned engine sources (override with --build-arg KLIPPER_TAG=... / KALICO_TAG=...).
RUN git clone --depth 1 --branch "${KLIPPER_TAG}" \
        https://github.com/Klipper3d/klipper.git /opt/klipper \
    && git clone --depth 1 --branch "${KALICO_TAG}" \
        https://github.com/KalicoCrew/kalico.git /opt/kalico \
    && printf 'KLIPPER_TAG=%s\nKALICO_TAG=%s\nTOOLCHAIN_VERSION=%s\n' \
        "${KLIPPER_TAG}" "${KALICO_TAG}" "${TOOLCHAIN_VERSION}" > /opt/engines.env

COPY scripts/build_firmware.sh /opt/build_firmware.sh
COPY firmware/container/fwtool.sh /opt/fwtool.sh
COPY firmware/container/fwcheck.py /opt/fwcheck.py
RUN chmod +x /opt/build_firmware.sh /opt/fwtool.sh

ENV FW_TOOLCHAIN_BIN=/opt/gcc-arm-none-eabi-${TOOLCHAIN_VERSION}/bin \
    FW_TOOLCHAIN_VERSION=${TOOLCHAIN_VERSION} \
    FW_CONFIGS_DIR=/configs \
    FW_OUTPUT_DIR=/out \
    FW_CHECK_TOOL=/opt/fwcheck.py \
    LANG=C.UTF-8

WORKDIR /out
ENTRYPOINT ["/opt/fwtool.sh"]
CMD ["--help"]
