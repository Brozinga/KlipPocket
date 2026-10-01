# KlipPocket — MCU firmware builder
#
# Reproducible Klipper firmware for any supported board: pinned ARM toolchain +
# pinned Klipper tag, no obfuscation. See docs/build-firmware.md.
#
# Build context is the REPO ROOT (needs scripts/build_firmware.sh):
#   docker build -t klippocket-fw -f firmware/Dockerfile .
#   docker run --rm -v "$PWD/firmware/configs:/configs:ro" -v "$PWD/firmware/output:/out" \
#       klippocket-fw elegoo-neptune3-pro
#
# or just:  docker compose -f firmware/docker-compose.yml run --rm fw elegoo-neptune3-pro
FROM debian:bookworm-slim

ARG TOOLCHAIN_VERSION=10.3-2021.07
ARG KLIPPER_TAG=v0.13.0

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential make python3 python-is-python3 git \
        wget ca-certificates bzip2 xz-utils libncursesw6 \
    && rm -rf /var/lib/apt/lists/*

# Pinned ARM toolchain — the exact compiler used for the verified vendor images.
RUN wget -qO /tmp/tc.tar.bz2 \
        "https://developer.arm.com/-/media/Files/downloads/gnu-rm/${TOOLCHAIN_VERSION}/gcc-arm-none-eabi-${TOOLCHAIN_VERSION}-x86_64-linux.tar.bz2" \
    && mkdir -p /opt && tar xjf /tmp/tc.tar.bz2 -C /opt && rm /tmp/tc.tar.bz2

# Pinned Klipper source (override with --build-arg KLIPPER_TAG=vX.Y.Z).
RUN git clone --depth 1 --branch "${KLIPPER_TAG}" \
        https://github.com/Klipper3d/klipper.git /opt/klipper

COPY scripts/build_firmware.sh /opt/build_firmware.sh
RUN chmod +x /opt/build_firmware.sh

ENV FW_TOOLCHAIN_BIN=/opt/gcc-arm-none-eabi-${TOOLCHAIN_VERSION}/bin \
    FW_TOOLCHAIN_VERSION=${TOOLCHAIN_VERSION} \
    FW_KLIPPER_SRC=/opt/klipper \
    FW_CONFIGS_DIR=/configs \
    FW_OUTPUT_DIR=/out

WORKDIR /out
ENTRYPOINT ["/opt/build_firmware.sh"]
CMD ["--help"]
