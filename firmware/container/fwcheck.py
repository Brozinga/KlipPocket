#!/usr/bin/env python3
"""Manifest + validation helper for the KlipPocket firmware builder.

  fwcheck.py manifest --out-dir D --base B --engine E --tag T --version V
                      --config CFG --used-config USED --files F...
  fwcheck.py validate --out-dir D --base B --config CFG --engine E --tag T
  fwcheck.py compare  --a DIR_A --b DIR_B --base B
  fwcheck.py matchbuild --out-dir D --base B --build-out OUT   (rebuilt == validated?)
  fwcheck.py probe    --port P --dict FILE --msgproto klippy/msgproto.py
                      [--baud 250000] [--timeout 20]           (what runs on the board?)

`manifest` is called by scripts/build_firmware.sh after a build. `validate`
re-checks what is on disk: hashes, the image format, and that the image really
was built from the saved configuration (via the klipper.dict the build emits).
Exit status: 0 = all good (warnings allowed), 1 = at least one check failed.
"""
import argparse
import hashlib
import importlib.util
import json
import os
import sys
import time
import zlib

GREEN, YELLOW, RED, DIM, RESET = "\033[32m", "\033[33m", "\033[31m", "\033[2m", "\033[0m"
if not sys.stdout.isatty():
    GREEN = YELLOW = RED = DIM = RESET = ""


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_config(path):
    """CONFIG_* values of a Kconfig .config (strings unquoted, 'n' for 'is not set')."""
    cfg = {}
    if not os.path.isfile(path):
        return cfg
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if line.startswith("CONFIG_") and "=" in line:
                k, v = line.split("=", 1)
                cfg[k] = v.strip('"')
            elif line.startswith("# CONFIG_") and line.endswith("is not set"):
                cfg[line.split()[1]] = "n"
    return cfg


def to_int(v):
    try:
        return int(str(v), 0)
    except (TypeError, ValueError):
        return None


def load_dict(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


# ---------------------------------------------------------------- manifest
def cmd_manifest(a):
    files = []
    for name in a.files:
        p = os.path.join(a.out_dir, name)
        files.append({"name": name, "bytes": os.path.getsize(p), "sha256": sha256(p)})
    man = {
        "engine": a.engine,
        "tag": a.tag,
        "version": a.version,
        "built_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "config_sha256": sha256(a.config),
        "used_config_sha256": sha256(a.used_config) if a.used_config else None,
        "files": files,
    }
    with open(os.path.join(a.out_dir, a.base + ".manifest.json"), "w", encoding="utf-8") as f:
        json.dump(man, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- validate
class Report:
    def __init__(self):
        self.failed = 0
        self.warned = 0

    def ok(self, msg):
        print("  %s[ OK ]%s %s" % (GREEN, RESET, msg))

    def warn(self, msg):
        self.warned += 1
        print("  %s[WARN]%s %s" % (YELLOW, RESET, msg))

    def fail(self, msg):
        self.failed += 1
        print("  %s[FAIL]%s %s" % (RED, RESET, msg))


def check_ihex(path, r):
    data_records, end = 0, False
    try:
        with open(path, encoding="ascii") as f:
            for n, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                if line[0] != ":":
                    return r.fail("%s: line %d is not an Intel HEX record" % (os.path.basename(path), n))
                raw = bytes.fromhex(line[1:])
                if sum(raw) & 0xFF:
                    return r.fail("%s: bad checksum on line %d" % (os.path.basename(path), n))
                if raw[3] == 0:
                    data_records += 1
                elif raw[3] == 1:
                    end = True
    except (OSError, ValueError) as e:
        return r.fail("%s: not a valid Intel HEX file (%s)" % (os.path.basename(path), e))
    if not data_records or not end:
        return r.fail("%s: HEX has no data or no end-of-file record" % os.path.basename(path))
    r.ok("%s: Intel HEX valid (%d data records, checksums good)" % (os.path.basename(path), data_records))


def check_uf2(path, r):
    blob = open(path, "rb").read()
    name = os.path.basename(path)
    if len(blob) % 512 or not blob:
        return r.fail("%s: size is not a multiple of 512 bytes" % name)
    blocks = len(blob) // 512
    for i in range(blocks):
        b = blob[i * 512:(i + 1) * 512]
        m0, m1 = int.from_bytes(b[0:4], "little"), int.from_bytes(b[4:8], "little")
        mend = int.from_bytes(b[508:512], "little")
        total = int.from_bytes(b[24:28], "little")
        if (m0, m1, mend) != (0x0A324655, 0x9E5D5157, 0x0AB16F30):
            return r.fail("%s: block %d has bad UF2 magic numbers" % (name, i))
        if total != blocks:
            return r.fail("%s: block %d says %d blocks, file has %d" % (name, i, total, blocks))
    r.ok("%s: UF2 valid (%d blocks)" % (name, blocks))


def check_bin(path, cfg, r):
    name = os.path.basename(path)
    blob = open(path, "rb").read()
    mach = [k for k, v in cfg.items() if k.startswith("CONFIG_MACH_") and v == "y"]
    arm_vector = any(m in mach for m in ("CONFIG_MACH_STM32", "CONFIG_MACH_ATSAM",
                                         "CONFIG_MACH_ATSAMD", "CONFIG_MACH_LPC176X",
                                         "CONFIG_MACH_HC32F460"))
    if not arm_vector or len(blob) < 8:
        r.ok("%s: %d bytes, non-empty (no vector-table check for this MCU family)" % (name, len(blob)))
        return
    sp = int.from_bytes(blob[0:4], "little")
    rv = int.from_bytes(blob[4:8], "little")
    app = to_int(cfg.get("CONFIG_FLASH_APPLICATION_ADDRESS"))
    if not (0x10000000 <= sp <= 0x30000000):
        return r.fail("%s: first word 0x%08x is not a RAM stack pointer (wrong image?)" % (name, sp))
    if not rv & 1:
        return r.fail("%s: reset vector 0x%08x lacks the Thumb bit (wrong image?)" % (name, rv))
    if app is not None and not (app <= (rv & ~1) < app + len(blob)):
        return r.fail("%s: reset vector 0x%08x is outside the image linked at 0x%08x"
                      " (wrong bootloader offset?)" % (name, rv, app))
    r.ok("%s: ARM vector table sane (SP 0x%08x, reset 0x%08x%s)"
         % (name, sp, rv, "" if app is None else ", linked at 0x%08x" % app))


def cmd_validate(a):
    r = Report()
    man_path = os.path.join(a.out_dir, a.base + ".manifest.json")
    print("Validating %s (%s %s)" % (a.base, a.engine, a.tag))
    if not os.path.isfile(man_path):
        r.fail("no manifest (%s.manifest.json): the firmware has not been built yet" % a.base)
        return 1
    man = json.load(open(man_path, encoding="utf-8"))
    # facts come from the reconciled .config the build really used (a hand-made
    # profile may be partial); the saved profile only matters for the freshness check
    used = os.path.join(a.out_dir, a.base + ".config.used")
    cfg = read_config(used if os.path.isfile(used) else a.config)

    # 1. files + hashes
    images = []
    for f in man["files"]:
        p = os.path.join(a.out_dir, f["name"])
        if not os.path.isfile(p):
            r.fail("%s is missing" % f["name"])
        elif os.path.getsize(p) == 0:
            r.fail("%s is empty" % f["name"])
        elif sha256(p) != f["sha256"]:
            r.fail("%s changed since the build (sha256 mismatch)" % f["name"])
        else:
            r.ok("%s: %d bytes, sha256 %s…" % (f["name"], f["bytes"], f["sha256"][:16]))
            images.append(p)

    # 2. image format
    for p in images:
        if p.endswith(".hex"):
            check_ihex(p, r)
        elif p.endswith(".uf2"):
            check_uf2(p, r)
        elif p.endswith(".bin"):
            check_bin(p, cfg, r)
        elif p.endswith(".elf"):
            if open(p, "rb").read(4) == b"\x7fELF":
                r.ok("%s: ELF header valid" % os.path.basename(p))
            else:
                r.fail("%s: not an ELF file" % os.path.basename(p))

    # 3. saved configuration is the one that was built
    if sha256(a.config) == man.get("config_sha256"):
        r.ok("configuration unchanged since the build")
    else:
        r.warn("firmware/configs/%s changed after the build — rebuild to apply it"
               % os.path.basename(a.config))

    # 4. what the MCU will report to the host (klipper.dict)
    d = load_dict(os.path.join(a.out_dir, a.base + ".dict"))
    if d is None:
        r.fail("%s.dict missing or unreadable (the host needs it to talk to the MCU)" % a.base)
    else:
        dcfg = d.get("config", {})
        mcu_cfg, mcu_dict = cfg.get("CONFIG_MCU"), dcfg.get("MCU")
        if mcu_cfg and mcu_dict and mcu_cfg != mcu_dict:
            r.fail("image was built for MCU '%s' but the configuration says '%s'" % (mcu_dict, mcu_cfg))
        elif mcu_dict:
            r.ok("MCU in the image: %s" % mcu_dict)
        ver = d.get("version", "")
        if ver and a.tag and ver.startswith(a.tag):
            r.ok("firmware version: %s" % ver)
        elif ver:
            r.warn("firmware version %s does not start with the engine tag %s" % (ver, a.tag))
        else:
            r.warn("no version string in the dictionary")
        want = to_int(cfg.get("CONFIG_CLOCK_FREQ"))
        got = to_int(dcfg.get("CLOCK_FREQ"))
        if want and got and want != got:
            r.fail("clock %s Hz in the image, %s Hz in the configuration" % (got, want))
        elif got:
            r.ok("clock: %.0f MHz" % (got / 1e6))
        baud = to_int(dcfg.get("SERIAL_BAUD")) or to_int(cfg.get("CONFIG_SERIAL_BAUD"))
        usb = cfg.get("CONFIG_USBSERIAL") == "y" or any(
            k.startswith("CONFIG_USB") and v == "y" for k, v in cfg.items())
        if baud is not None and baud != 250000:
            r.warn("serial baud is %s — KlipPocket only supports 250000" % baud)
        elif baud == 250000:
            r.ok("serial baud 250000 (what KlipPocket expects)")
        elif usb:
            r.ok("USB communication, id %s:%s (no UART baud to match)" % (
                cfg.get("CONFIG_USB_VENDOR_ID", "?").replace("0x", ""),
                cfg.get("CONFIG_USB_DEVICE_ID", "?").replace("0x", "")))
        else:
            r.warn("could not tell the communication interface from the configuration")

    # 5. bootloader offset vs flash size
    boot = to_int(cfg.get("CONFIG_FLASH_BOOT_ADDRESS"))
    app = to_int(cfg.get("CONFIG_FLASH_APPLICATION_ADDRESS"))
    size = to_int(cfg.get("CONFIG_FLASH_SIZE"))
    if boot is not None and app is not None:
        if app == boot and cfg.get("CONFIG_MCU") in ("rp2040", "rp2350"):
            r.ok("RP2040/RP2350: ROM bootloader, nothing to preserve in flash")
        elif app == boot:
            r.warn("no bootloader offset (image starts at the very first flash address)")
        else:
            r.ok("bootloader offset: 0x%x bytes kept free" % (app - boot))
        for f in man["files"]:
            if f["name"].endswith(".bin") and size and f["bytes"] > size - (app - boot):
                r.fail("%s does not fit in the application area" % f["name"])

    print()
    if r.failed:
        print("%s%d check(s) failed — DO NOT flash this firmware.%s" % (RED, r.failed, RESET))
        return 1
    print("%sAll checks passed%s%s." % (GREEN, RESET,
          " (%d warning(s))" % r.warned if r.warned else ""))
    return 0


def cmd_compare(a):
    ma = json.load(open(os.path.join(a.a, a.base + ".manifest.json")))
    mb = json.load(open(os.path.join(a.b, a.base + ".manifest.json")))
    ha = {f["name"]: f["sha256"] for f in ma["files"]}
    hb = {f["name"]: f["sha256"] for f in mb["files"]}
    if ha == hb:
        print("  %s[ OK ]%s rebuilt from scratch: identical hashes (reproducible)" % (GREEN, RESET))
        return 0
    print("  %s[FAIL]%s the rebuild differs from the first build" % (RED, RESET))
    return 1


# The file name of each deliverable in `make`'s out/ directory.
BUILD_NAMES = {".bin": "klipper.bin", ".hex": "klipper.elf.hex", ".uf2": "klipper.uf2",
               ".elf": "klipper.elf"}


def cmd_matchbuild(a):
    """The firmware `make flash` is about to write must be byte-identical to the
    one that was validated (same sources, compiler and configuration)."""
    man = json.load(open(os.path.join(a.out_dir, a.base + ".manifest.json"), encoding="utf-8"))
    bad = 0
    for f in man["files"]:
        built = os.path.join(a.build_out, BUILD_NAMES.get(os.path.splitext(f["name"])[1], "?"))
        if not os.path.isfile(built):
            print("  %s[FAIL]%s %s was not produced by the rebuild" % (RED, RESET, f["name"]))
            bad += 1
        elif sha256(built) != f["sha256"]:
            print("  %s[FAIL]%s the rebuilt %s differs from the validated one" % (RED, RESET, f["name"]))
            bad += 1
        else:
            print("  %s[ OK ]%s %s is identical to the validated build" % (GREEN, RESET, f["name"]))
    return 1 if bad else 0


# ------------------------------------------------------------------ probe
def _load_msgproto(path):
    spec = importlib.util.spec_from_file_location("klipper_msgproto", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def read_identify(port, baud, mp, budget):
    """Ask the MCU for its data dictionary, the same way klippy does on connect."""
    import serial  # pyserial (python3-serial in the image)
    parser = mp.MessageParser()
    ser = serial.Serial(port, baud, timeout=0.1)
    try:
        ser.write(bytes([mp.MESSAGE_SYNC]))
        data, seq, deadline = b"", 1, time.time() + budget
        buf = bytearray()
        while time.time() < deadline:
            cmd = parser.create_command("identify offset=%d count=%d" % (len(data), 40))
            flat = []  # encode_msgblock nests the 2-byte CRC as a list
            for x in parser.encode_msgblock(seq, cmd):
                flat.extend(x if isinstance(x, list) else [x])
            ser.write(bytes(flat))
            end, answer = time.time() + 0.6, None
            while time.time() < end and answer is None:
                buf += ser.read(ser.in_waiting or 1)  # returns as soon as bytes arrive
                while buf:
                    if buf[0] == mp.MESSAGE_SYNC:
                        del buf[0]
                        continue
                    n = parser.check_packet(bytes(buf))
                    if n == 0:
                        break
                    if n < 0:
                        i = buf.find(bytes([mp.MESSAGE_SYNC]))
                        del buf[:(i + 1 if i >= 0 else len(buf))]
                        continue
                    pkt = bytes(buf[:n])
                    del buf[:n]
                    seq = pkt[mp.MESSAGE_POS_SEQ] & mp.MESSAGE_SEQ_MASK  # next seq the MCU wants
                    if n > mp.MESSAGE_MIN:
                        params = parser.parse(pkt)
                        if params.get("#name") == "identify_response" and params["offset"] == len(data):
                            answer = params["data"]
            if answer is None:
                continue
            if not answer:
                return zlib.decompress(data)
            data += bytes(answer)
        return None
    finally:
        ser.close()


def cmd_probe(a):
    expected = load_dict(a.dict)
    if expected is None:
        print("  %s[FAIL]%s cannot read %s (build the firmware first)" % (RED, RESET, a.dict))
        return 1
    mp = _load_msgproto(a.msgproto)
    deadline = time.time() + a.timeout
    print("Connecting to %s (up to %ds)..." % (a.port, a.timeout))
    raw, last = None, None
    while time.time() < deadline and raw is None:
        if not os.path.exists(a.port):
            time.sleep(0.5)
            continue
        try:
            raw = read_identify(a.port, a.baud, mp, min(15, max(2, deadline - time.time())))
        except Exception as e:  # port busy, permission, garbage on the line...
            last = e
            time.sleep(0.5)
    if raw is None:
        why = " (%s)" % last if last else ""
        print("  %s[FAIL]%s the board did not answer on %s%s" % (RED, RESET, a.port, why))
        print("         Is the firmware running? Is the port free (close the app/printer profile using it)?")
        return 1
    try:
        running = json.loads(raw)
    except ValueError:
        print("  %s[FAIL]%s the board answered, but not with a Klipper dictionary" % (RED, RESET))
        return 1
    rc = running.get("config", {})
    ec = expected.get("config", {})
    print("  Board reports: %s, MCU %s, %s MHz" % (running.get("version", "?"), rc.get("MCU", "?"),
                                                  "%.0f" % (int(rc.get("CLOCK_FREQ", 0)) / 1e6)))
    if running == expected:
        print("  %s[ OK ]%s the board runs exactly the firmware that was built (identical dictionary)" % (GREEN, RESET))
        return 0
    if rc.get("MCU") != ec.get("MCU"):
        print("  %s[FAIL]%s wrong chip: the board is %s, the build is for %s" % (RED, RESET, rc.get("MCU"), ec.get("MCU")))
    elif running.get("version") != expected.get("version"):
        print("  %s[FAIL]%s the board runs %s, the build is %s — the flash did not take effect"
              % (RED, RESET, running.get("version"), expected.get("version")))
    else:
        print("  %s[FAIL]%s same version, but a different build/configuration is running on the board" % (RED, RESET))
    return 1


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("manifest")
    for k in ("out-dir", "base", "engine", "tag", "version", "config"):
        m.add_argument("--" + k, required=True)
    m.add_argument("--used-config")
    m.add_argument("--files", nargs="+", required=True)
    v = sub.add_parser("validate")
    for k in ("out-dir", "base", "config", "engine", "tag"):
        v.add_argument("--" + k, required=True)
    c = sub.add_parser("compare")
    for k in ("a", "b", "base"):
        c.add_argument("--" + k, required=True)
    mb = sub.add_parser("matchbuild")
    for k in ("out-dir", "base", "build-out"):
        mb.add_argument("--" + k, required=True)
    pr = sub.add_parser("probe")
    for k in ("port", "dict", "msgproto"):
        pr.add_argument("--" + k, required=True)
    pr.add_argument("--baud", type=int, default=250000)
    pr.add_argument("--timeout", type=int, default=20)
    a = ap.parse_args()
    a.__dict__ = {k.replace("-", "_"): val for k, val in vars(a).items()}
    return {"manifest": lambda x: cmd_manifest(x) or 0, "validate": cmd_validate,
            "compare": cmd_compare, "matchbuild": cmd_matchbuild,
            "probe": cmd_probe}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
