"""Tests for firmware/container/fwcheck.py (image validation of the firmware builder)."""
import hashlib
import importlib.util
import json
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "..", "firmware", "container"))
import fwcheck  # noqa: E402
sys.path.insert(0, HERE)
import fake_mcu  # noqa: E402


def ihex(records):
    out = []
    for rtype, addr, data in records:
        raw = bytes([len(data), addr >> 8, addr & 0xFF, rtype]) + bytes(data)
        out.append(":" + (raw + bytes([(-sum(raw)) & 0xFF])).hex().upper())
    return "\n".join(out) + "\n"


def uf2(blocks):
    out = b""
    for i in range(blocks):
        b = bytearray(512)
        b[0:4] = (0x0A324655).to_bytes(4, "little")
        b[4:8] = (0x9E5D5157).to_bytes(4, "little")
        b[24:28] = blocks.to_bytes(4, "little")
        b[508:512] = (0x0AB16F30).to_bytes(4, "little")
        out += bytes(b)
    return out


class Rep(fwcheck.Report):
    def __init__(self):
        super().__init__()
        self.msgs = []

    def ok(self, m): self.msgs.append(("ok", m))
    def warn(self, m): self.warned += 1; self.msgs.append(("warn", m))
    def fail(self, m): self.failed += 1; self.msgs.append(("fail", m))


class ImageChecks(unittest.TestCase):
    def write(self, name, data, mode="wb"):
        p = os.path.join(self.tmp, name)
        with open(p, mode) as f:
            f.write(data)
        return p

    def setUp(self):
        self._t = tempfile.TemporaryDirectory()
        self.tmp = self._t.name

    def tearDown(self):
        self._t.cleanup()

    def test_ihex_valid(self):
        p = self.write("a.hex", ihex([(0, 0, [1, 2, 3]), (1, 0, [])]), "w")
        r = Rep(); fwcheck.check_ihex(p, r)
        self.assertEqual(r.failed, 0)

    def test_ihex_bad_checksum(self):
        text = ihex([(0, 0, [1, 2, 3]), (1, 0, [])]).replace("010203", "010204", 1)
        r = Rep(); fwcheck.check_ihex(self.write("a.hex", text, "w"), r)
        self.assertEqual(r.failed, 1)

    def test_ihex_missing_eof(self):
        r = Rep(); fwcheck.check_ihex(self.write("a.hex", ihex([(0, 0, [1])]), "w"), r)
        self.assertEqual(r.failed, 1)

    def test_uf2(self):
        r = Rep(); fwcheck.check_uf2(self.write("a.uf2", uf2(3)), r)
        self.assertEqual(r.failed, 0)
        r = Rep(); fwcheck.check_uf2(self.write("b.uf2", uf2(3)[:-1]), r)
        self.assertEqual(r.failed, 1)

    def test_arm_bin_vector_table(self):
        cfg = {"CONFIG_MACH_STM32": "y", "CONFIG_FLASH_APPLICATION_ADDRESS": "0x8008000"}
        good = (0x20010000).to_bytes(4, "little") + (0x0800E195).to_bytes(4, "little") + bytes(0x7000)
        r = Rep(); fwcheck.check_bin(self.write("g.bin", good), cfg, r)
        self.assertEqual(r.failed, 0)
        # image linked for a different bootloader offset
        cfg2 = dict(cfg, CONFIG_FLASH_APPLICATION_ADDRESS="0x8002000")
        r = Rep(); fwcheck.check_bin(self.write("g2.bin", good), cfg2, r)
        self.assertEqual(r.failed, 1)
        bad = bytes(8) + bytes(100)
        r = Rep(); fwcheck.check_bin(self.write("b.bin", bad), cfg, r)
        self.assertEqual(r.failed, 1)

    def test_read_config(self):
        p = self.write("c.config", '# x\nCONFIG_MCU="stm32f103xe"\n# CONFIG_A is not set\nCONFIG_B=y\n', "w")
        self.assertEqual(fwcheck.read_config(p),
                         {"CONFIG_MCU": "stm32f103xe", "CONFIG_A": "n", "CONFIG_B": "y"})


class ValidateFlow(unittest.TestCase):
    def build(self, tmp, image):
        out = os.path.join(tmp, "out"); os.makedirs(out)
        cfg = os.path.join(tmp, "p.config")
        with open(cfg, "w") as f:
            f.write('CONFIG_MCU="atmega2560"\nCONFIG_CLOCK_FREQ=16000000\nCONFIG_SERIAL_BAUD=250000\n')
        with open(os.path.join(out, "p-klipper.hex"), "w") as f:
            f.write(image)
        with open(os.path.join(out, "p-klipper.dict"), "w") as f:
            json.dump({"version": "v0.13.0-0-gabc", "config": {"MCU": "atmega2560", "CLOCK_FREQ": 16000000}}, f)
        with open(os.path.join(out, "p-klipper.config.used"), "w") as f:
            f.write(open(cfg).read())
        args = type("A", (), dict(out_dir=out, base="p-klipper", engine="klipper", tag="v0.13.0",
                                  version="v0.13.0", config=cfg, used_config=cfg,
                                  files=["p-klipper.hex"]))
        fwcheck.cmd_manifest(args)
        return args

    def test_good_then_tampered(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = self.build(tmp, ihex([(0, 0, [1, 2, 3]), (1, 0, [])]))
            self.assertEqual(fwcheck.cmd_validate(a), 0)
            with open(os.path.join(a.out_dir, "p-klipper.hex"), "a") as f:
                f.write("\n")
            self.assertEqual(fwcheck.cmd_validate(a), 1)

    def test_wrong_mcu_in_dict(self):
        with tempfile.TemporaryDirectory() as tmp:
            a = self.build(tmp, ihex([(0, 0, [1]), (1, 0, [])]))
            with open(os.path.join(a.out_dir, "p-klipper.dict"), "w") as f:
                json.dump({"version": "v0.13.0", "config": {"MCU": "stm32f103xe"}}, f)
            self.assertEqual(fwcheck.cmd_validate(a), 1)


@unittest.skipUnless(importlib.util.find_spec("serial"), "pyserial not installed (it is in the firmware image)")
class Probe(unittest.TestCase):
    """`fwcheck probe` against a pretend MCU (real msgproto framing, no hardware)."""
    EXPECTED = {"version": "v0.13.0-0-gabc", "build_versions": "gcc: test",
                "config": {"MCU": "stm32f103xe", "CLOCK_FREQ": 72000000},
                "commands": {"identify offset=%u count=%c": 1}}

    def probe(self, running, expected):
        mcu = fake_mcu.FakeMCU(running)
        mcu.start()
        try:
            with tempfile.TemporaryDirectory() as tmp:
                dpath = os.path.join(tmp, "x.dict")
                with open(dpath, "w") as f:
                    json.dump(expected, f)
                args = type("A", (), dict(port=mcu.port, dict=dpath, msgproto=fake_mcu.MSGPROTO,
                                          baud=250000, timeout=10))
                return fwcheck.cmd_probe(args)
        finally:
            mcu.stop()

    def test_identical_firmware(self):
        self.assertEqual(self.probe(self.EXPECTED, self.EXPECTED), 0)

    def test_other_version_is_a_failure(self):
        old = dict(self.EXPECTED, version="v0.12.0-0-gdef")
        self.assertEqual(self.probe(old, self.EXPECTED), 1)

    def test_other_chip_is_a_failure(self):
        other = dict(self.EXPECTED, config={"MCU": "atmega2560", "CLOCK_FREQ": 16000000})
        self.assertEqual(self.probe(other, self.EXPECTED), 1)

    def test_no_board(self):
        args = type("A", (), dict(port="/nonexistent/port", dict=os.devnull, msgproto=fake_mcu.MSGPROTO,
                                  baud=250000, timeout=1))
        with tempfile.TemporaryDirectory() as tmp:
            dpath = os.path.join(tmp, "x.dict")
            with open(dpath, "w") as f:
                json.dump(self.EXPECTED, f)
            args.dict = dpath
            self.assertEqual(fwcheck.cmd_probe(args), 1)


if __name__ == "__main__":
    unittest.main()
