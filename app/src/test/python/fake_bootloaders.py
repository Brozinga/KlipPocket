"""Pretend bootloaders (Katapult, STM32 HID, Atmel SAM-BA) as byte-stream devices, for testing the app's flashers.

Each class is a pure state machine: `feed(bytes) -> bytes` takes what the host wrote and returns what the board answers.
They are checked against the real tools in test_bootloader_fakes.py (Klipper's flash_can.py, hid-flash and bossac run
against them), and the Kotlin tests drive the app's code against the very same fakes through stdio:

    python3 fake_bootloaders.py katapult|hid|samba [--dump FILE] [--fault NAME]

The process reads the host's bytes on stdin, writes the answers on stdout and, when stdin closes, writes the board
state (flash SHA-1, flags) as JSON to --dump.
"""
import argparse
import hashlib
import json
import os
import struct
import sys


def crc16_ccitt(buf):
    crc = 0xFFFF
    for data in buf:
        data ^= crc & 0xFF
        data ^= (data & 0x0F) << 4
        crc = ((data << 8) | (crc >> 8)) ^ (data >> 4) ^ (data << 3)
    return crc & 0xFFFF


class FakeKatapult:
    """Katapult / CanBoot as seen on a byte stream (USB CDC or UART)."""
    HEADER = b"\x01\x88"
    TRAILER = b"\x99\x03"
    CMD = {0x11: "CONNECT", 0x12: "SEND_BLOCK", 0x13: "SEND_EOF", 0x14: "REQUEST_BLOCK", 0x15: "COMPLETE", 0x16: "GET_CANBUS_ID"}

    def __init__(self, block=64, app_start=0x08002000, flash_size=0x20000, mcu=b"stm32f446xx", uuid=0x112233445566, fault=None):
        self.block, self.app_start, self.mcu, self.uuid = block, app_start, mcu, uuid
        self.flash = bytearray(b"\xff" * flash_size)
        self.rx = bytearray()
        self.eof = False
        self.complete = False
        self.fault = fault           # "garble-first", "no-answer", "bad-write", "bad-read"
        self.answers = 0
        self.log = []

    def _frame(self, cmd, payload=b"", ack=0xA0):
        body = bytes([ack, (4 + len(payload)) // 4]) + struct.pack("<I", cmd) + payload
        return self.HEADER + body + struct.pack("<H", crc16_ccitt(body)) + self.TRAILER

    def feed(self, data):
        self.rx += data
        out = b""
        while True:
            i = self.rx.find(self.HEADER)
            if i < 0:
                self.rx.clear()
                break
            del self.rx[:i]
            if len(self.rx) < 4:
                break
            total = self.rx[3] * 4 + 8
            if len(self.rx) < total:
                break
            frame = bytes(self.rx[:total])
            del self.rx[:total]
            out += self._handle(frame)
        return out

    def _handle(self, frame):
        cmd, words = frame[2], frame[3]
        payload = frame[4:4 + words * 4]
        crc, = struct.unpack("<H", frame[-4:-2])
        if frame[-2:] != self.TRAILER or crc != crc16_ccitt(frame[2:-4]):
            return self._frame(cmd, ack=0xF1)
        name = self.CMD.get(cmd)
        self.log.append(name)
        if self.fault == "no-answer":
            return b""
        if self.fault == "garble-first" and self.answers == 0:
            self.answers += 1
            return b"\x01\x88\xa0\x01garbage\x99\x03"
        self.answers += 1
        if name == "CONNECT":
            return self._frame(cmd, b"\x00\x01\x02\x00" + struct.pack("<II", self.app_start, self.block)
                               + self.mcu.ljust((len(self.mcu) + 3) // 4 * 4, b"\x00"))
        if name == "GET_CANBUS_ID":
            return self._frame(cmd, self.uuid.to_bytes(6, "big") + b"\x00\x00")
        if name == "SEND_BLOCK":
            addr, = struct.unpack("<I", payload[:4])
            block = payload[4:]
            off = addr - self.app_start
            if len(block) != self.block or off < 0 or off + self.block > len(self.flash) or off % self.block:
                return self._frame(cmd, ack=0xF1)
            if self.fault == "bad-write":
                block = bytes([block[0] ^ 0xFF]) + block[1:]
            self.flash[off:off + self.block] = block
            return self._frame(cmd, struct.pack("<I", addr))
        if name == "SEND_EOF":
            self.eof = True
            return self._frame(cmd, struct.pack("<I", len([1 for i in range(0, len(self.flash), 2048) if self.flash[i:i + 2048] != b"\xff" * 2048])))
        if name == "REQUEST_BLOCK":
            addr, = struct.unpack("<I", payload[:4])
            off = addr - self.app_start
            if off < 0 or off + self.block > len(self.flash):
                return self._frame(cmd, ack=0xF1)
            block = bytes(self.flash[off:off + self.block])
            if self.fault == "bad-read":
                block = bytes([block[0] ^ 0xFF]) + block[1:]
            return self._frame(cmd, struct.pack("<I", addr) + block)
        if name == "COMPLETE":
            self.complete = True
            return self._frame(cmd)
        return self._frame(cmd, ack=0xF1)

    def state(self):
        used = len(self.flash.rstrip(b"\xff"))
        return {"complete": self.complete, "eof": self.eof, "flash_sha1": hashlib.sha1(bytes(self.flash[:used])).hexdigest(),
                "used": used, "log": self.log}


class FakeHid:
    """The STM32 HID bootloader (1209:beba): 64-byte output reports in, 8-byte input reports out."""
    OUT, IN = 64, 8

    def __init__(self, page=1024, flash_size=0x20000, fault=None):
        self.page = page
        self.flash = bytearray(b"\x00" * flash_size)    # pages are filled as they arrive
        self.pending = bytearray()
        self.offset = 0
        self.reset_pages = False
        self.rebooted = False
        self.fault = fault           # "no-ack"
        self.acks = []

    def _ack(self, code):
        self.acks.append(code)
        return b"" if self.fault == "no-ack" else b"BTLDCMD" + bytes([code])

    def report(self, data):
        assert len(data) == self.OUT, len(data)
        if data[:7] == b"BTLDCMD":
            if data[7] == 0:
                self.reset_pages = True
                self.offset = 0
                self.pending.clear()
            elif data[7] == 1:
                self.rebooted = True
            return b""
        if not self.reset_pages:
            return b""
        self.pending += data
        if len(self.pending) < 1024:
            return b""
        sector, self.pending = bytes(self.pending[:1024]), bytearray(self.pending[1024:])
        self.flash[self.offset:self.offset + 1024] = sector
        self.offset += 1024
        # a 2 KiB-page chip answers 0x03 after the first half of a page and 0x02 when the page is written
        return self._ack(0x02 if self.offset % self.page == 0 else 0x03)

    def feed(self, data):
        out = b""
        for i in range(0, len(data), self.OUT):
            out += self.report(data[i:i + self.OUT])
        return out

    def state(self):
        used = len(self.flash.rstrip(b"\x00"))
        return {"rebooted": self.rebooted, "flash_sha1": hashlib.sha1(bytes(self.flash[:self.offset])).hexdigest(),
                "written": self.offset, "acks": self.acks, "used": used}


class FakeSamba:
    """A SAM3X8E (or SAM4E8E) in its USB ROM bootloader: SAM-BA monitor, EEFC flash controller, bossac's word-copy applet."""
    APPLET_CODE = bytes([0x09, 0x48, 0x0a, 0x49, 0x0a, 0x4a, 0x02, 0xe0, 0x08, 0xc9, 0x08, 0xc0, 0x01, 0x3a, 0x00, 0x2a,
                         0xfa, 0xd1, 0x04, 0x48, 0x00, 0x28, 0x01, 0xd1, 0x01, 0x48, 0x85, 0x46, 0x70, 0x47, 0xc0, 0x46])
    CHIPS = {
        "sam3x8e": dict(cidr=0x285E0A60, cpu=0xC230, id_reg=0x400E0940, flash=0x80000, pages=2048, size=256, planes=2, regs=0x400E0A00,
                        reset_reg=0x400E1A00),
        "sam4e8e": dict(cidr=0xA3CC0CE0, cpu=0xC240, id_reg=0x400E0740, flash=0x400000, pages=1024, size=512, planes=1, regs=0x400E0A00,
                        reset_reg=0x400E1800, ext=0x00120208),
    }

    def __init__(self, chip="sam3x8e", fault=None):
        c = self.CHIPS[chip]
        self.c = c
        self.fault = fault           # "no-answer", "bad-write"
        self.flash = bytearray(b"\x00" * (c["pages"] * c["size"]))    # a used board: everything is programmed until erased
        self.latch = {}              # word offset in the page -> value, per plane
        self.ram = {}                # address -> byte
        self.gpnvm = 0
        self.fcr_log = []
        self.fsr_error = 0
        self.rx = bytearray()
        self.binary = None           # (addr, remaining) while a 'S' payload is expected
        self.binary_buf = bytearray()
        self.reset = False
        self.erased = False
        self.cmd_count = 0

    # -- memory ---------------------------------------------------------------
    def _is_flash(self, a):
        return self.c["flash"] <= a < self.c["flash"] + len(self.flash)

    def read_word(self, a):
        c = self.c
        if a == 0:
            return 0x20001234
        if a == 0xE000ED00:
            return 0x410FC000 | c["cpu"]
        if a == 0x4 and c["cpu"] == 0xC240:
            return 0x00800123
        if a == c["id_reg"]:
            return c["cidr"]
        if a == c["id_reg"] + 4:
            return c.get("ext", 0)
        if a == 0x400E0740 and c["id_reg"] != a:
            return 0
        for plane in range(c["planes"]):
            base = c["regs"] + 0x200 * plane
            if a == base + 0x08:
                return 1 | self.fsr_error
            if a == base + 0x0C:
                return self.gpnvm if plane == 0 else 0
        if self._is_flash(a):
            return int.from_bytes(self.flash[a - c["flash"]:a - c["flash"] + 4], "little")
        return int.from_bytes(bytes(self.ram.get(a + i, 0) for i in range(4)), "little")

    def write_word(self, a, v):
        c = self.c
        for plane in range(c["planes"]):
            base = c["regs"] + 0x200 * plane
            if a == base + 0x00:
                return
            if a == base + 0x04:
                return self._fcr(plane, v)
        if a == c["reset_reg"] and (v >> 24) == 0xA5:
            self.reset = True
            return
        if self._is_flash(a):
            self.latch[a & ~3] = v           # writes to the flash window fill the page latch
            return
        for i in range(4):
            self.ram[a + i] = (v >> (8 * i)) & 0xFF

    def _fcr(self, plane, v):
        c = self.c
        if (v >> 24) != 0x5A:
            self.fsr_error |= 2
            return
        cmd, arg = v & 0xFF, (v >> 8) & 0xFFFF
        self.fcr_log.append((plane, cmd, arg))
        per_plane = c["pages"] // c["planes"]
        if cmd == 0x05:                       # erase all
            lo = plane * per_plane * c["size"]
            self.flash[lo:lo + per_plane * c["size"]] = b"\xff" * (per_plane * c["size"])
            self.erased = True
        elif cmd == 0x01:                     # write page (the flash can only clear bits)
            page = plane * per_plane + arg
            if page >= c["pages"]:
                self.fsr_error |= 2
                return
            off = page * c["size"]
            for w in range(c["size"] // 4):
                a = c["flash"] + off + 4 * w
                new = self.latch.get(a, 0xFFFFFFFF)
                old = int.from_bytes(self.flash[off + 4 * w:off + 4 * w + 4], "little")
                self.flash[off + 4 * w:off + 4 * w + 4] = (old & new).to_bytes(4, "little")
            if self.fault == "bad-write" and page == 3:
                self.flash[off] ^= 0xFF
            self.latch.clear()
        elif cmd == 0x0B:                     # set GPNVM
            self.gpnvm |= 1 << arg
        elif cmd == 0x0C:
            self.gpnvm &= ~(1 << arg)
        elif cmd == 0x0D:                     # get GPNVM: FRR already shows it
            pass
        else:
            self.fsr_error |= 2

    def _go(self, addr):
        sp, pc = self.read_word(addr), self.read_word(addr + 4)
        base = (pc & ~1)
        code = bytes(self.ram.get(base + i, 0) for i in range(len(self.APPLET_CODE)))
        if code != self.APPLET_CODE:
            return                              # not our applet: a crash on the real chip; nothing happens here
        dst, src, words = self.read_word(base + 0x28), self.read_word(base + 0x2C), self.read_word(base + 0x30)
        for i in range(words):
            self.write_word(dst + 4 * i, self.read_word(src + 4 * i))

    # -- SAM-BA text protocol ------------------------------------------------
    def feed(self, data):
        if self.fault == "no-answer":
            return b""
        self.rx += data
        out = b""
        while True:
            if self.binary:
                addr, left = self.binary
                take = bytes(self.rx[:left])
                del self.rx[:len(take)]
                self.binary_buf += take
                if len(take) < left:
                    self.binary = (addr, left - len(take))
                    break
                for i, b in enumerate(self.binary_buf):
                    self.ram[addr + i] = b
                self.binary_buf.clear()
                self.binary = None
                continue
            i = self.rx.find(b"#")
            if i < 0:
                break
            cmd = bytes(self.rx[:i]).decode("ascii", "replace")
            del self.rx[:i + 1]
            out += self._command(cmd)
        return out

    def _command(self, cmd):
        self.cmd_count += 1
        op, args = cmd[:1], cmd[1:]
        if op == "N":
            return b"\n\r"
        if op == "V":
            return b"v1.1 Dec 15 2010 19:25:04\n\r"
        if op == "w":
            return self.read_word(int(args.split(",")[0], 16)).to_bytes(4, "little")
        if op == "W":
            a, v = args.split(",")
            self.write_word(int(a, 16), int(v, 16))
            return b""
        if op == "o":
            a = int(args.split(",")[0], 16)
            return bytes([self.ram.get(a, 0)])
        if op == "S":
            a, n = args.split(",")
            self.binary = (int(a, 16), int(n, 16))
            return b""
        if op == "R":
            a, n = [int(x, 16) for x in args.split(",")]
            return bytes(self.ram.get(a + i, 0) if not self._is_flash(a + i) else self.flash[a + i - self.c["flash"]] for i in range(n))
        if op == "G":
            self._go(int(args, 16))
            return b""
        return b""

    def state(self):
        used = len(self.flash.rstrip(b"\xff"))
        return {"reset": self.reset, "erased": self.erased, "gpnvm": self.gpnvm, "used": used,
                "flash_sha1": hashlib.sha1(bytes(self.flash[:used])).hexdigest(), "fcr": self.fcr_log}


class FakeCanBus:
    """A CAN bus with one node, first running Klipper and, after the reboot request, Katapult. Frames are 16 bytes ("<IB3x8s")."""
    FRAME = struct.Struct("<IB3x8s")
    ADMIN, ADMIN_RESP = 0x3F0, 0x3F1

    def __init__(self, uuid=0x112233445566, app="klipper", fault=None, **katapult):
        self.uuid = uuid
        self.app = app               # "klipper" or "katapult"
        self.fault = fault           # "silent" (the node never answers)
        self.node_id = None
        self.katapult = FakeKatapult(uuid=uuid, **katapult)
        self.rx = bytearray()
        self.seen = []

    def _uuid_bytes(self):
        return self.uuid.to_bytes(6, "big")

    def _reply(self, can_id, data):
        out = b""
        for i in range(0, len(data), 8):
            chunk = data[i:i + 8]
            out += self.FRAME.pack(can_id, len(chunk), chunk.ljust(8, b"\x00"))
        return out

    def frame(self, can_id, data):
        if self.fault == "silent":
            return b""
        if can_id == self.ADMIN:
            cmd = data[0] if data else None
            self.seen.append(("admin", cmd))
            if cmd == 0x02 and data[1:7] == self._uuid_bytes() and self.app == "klipper":
                self.app = "katapult"
                self.node_id = None
            elif cmd == 0x12:
                self.node_id = None
            elif cmd == 0x00 and self.node_id is None:
                return self._reply(self.ADMIN_RESP, bytes([0x20]) + self._uuid_bytes() + bytes([0x11 if self.app == "katapult" else 0x01]))
            elif cmd == 0x11 and data[1:7] == self._uuid_bytes() and self.app == "katapult":
                self.node_id = data[7]
            return b""
        if self.node_id is not None and self.app == "katapult" and can_id == self.node_id * 2 + 0x100:
            reply = self.katapult.feed(bytes(data))
            return self._reply(can_id + 1, reply) if reply else b""
        return b""

    def feed(self, data):
        self.rx += data
        out = b""
        while len(self.rx) >= self.FRAME.size:
            can_id, n, payload = self.FRAME.unpack(bytes(self.rx[:self.FRAME.size]))
            del self.rx[:self.FRAME.size]
            out += self.frame(can_id & 0x1FFFFFFF, payload[:n])
        return out

    def state(self):
        st = self.katapult.state()
        st.update({"app": self.app, "node_id": self.node_id, "admin": self.seen})
        return st


DEVICES = {"katapult": FakeKatapult, "hid": FakeHid, "samba": FakeSamba, "can": FakeCanBus}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("device", choices=sorted(DEVICES))
    ap.add_argument("--dump")
    ap.add_argument("--fault")
    ap.add_argument("--option", action="append", default=[], help="key=value constructor argument (integer or text)")
    a = ap.parse_args()
    opts = {}
    for kv in a.option:
        k, v = kv.split("=", 1)
        try:
            opts[k] = int(v, 0)
        except ValueError:
            opts[k] = v
    dev = DEVICES[a.device](fault=a.fault, **opts)
    fd_in, fd_out = sys.stdin.fileno(), sys.stdout.fileno()
    while True:
        chunk = os.read(fd_in, 65536)
        if not chunk:
            break
        reply = dev.feed(chunk)
        while reply:
            n = os.write(fd_out, reply)
            reply = reply[n:]
    if a.dump:
        with open(a.dump, "w") as f:
            json.dump(dev.state(), f)


if __name__ == "__main__":
    main()
