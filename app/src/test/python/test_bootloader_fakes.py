"""Checks the pretend bootloaders of fake_bootloaders.py against the real reference tools shipped in the Klipper tree.

If the real flash_can.py (Katapult), bossac (SAM-BA) and hid-flash run to a good end against a fake, the fake speaks the
protocol like the real board does, and the Kotlin tests can trust it. Tests are skipped when a tool cannot be built or run.
"""
import hashlib
import os
import pty
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import fake_bootloaders as fb  # noqa: E402

KLIPPER = os.path.join(HERE, "..", "..", "main", "klipper")
FLASH_CAN = os.path.join(KLIPPER, "lib", "canboot", "flash_can.py")
BOSSAC_SRC = os.path.join(KLIPPER, "lib", "bossac", "src")
HIDFLASH_SRC = os.path.join(KLIPPER, "lib", "hidflash")

BOSSAC_FILES = ("Samba Flash D5xNvmFlash D2xNvmFlash EfcFlash EefcFlash Applet WordCopyApplet WordCopyArm Flasher Device bossac "
                "CmdOpts LinuxPortFactory PosixSerialPort").split()

HID_SHIM = r"""
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <poll.h>
#include "hidapi.h"
/* hidapi on top of a socket: fd 3 carries the 64-byte output reports and the 8-byte input reports of the fake board */
struct hid_device_ { int fd; };
int hid_init(void) { return 0; }
int hid_exit(void) { return 0; }
struct hid_device_info *hid_enumerate(unsigned short v, unsigned short p) {
    struct hid_device_info *d = calloc(1, sizeof *d);
    d->vendor_id = v; d->product_id = p; d->release_number = 0x0300;
    return d;
}
void hid_free_enumeration(struct hid_device_info *d) { free(d); }
hid_device *hid_open(unsigned short v, unsigned short p, const wchar_t *s) { hid_device *h = malloc(sizeof *h); h->fd = 3; return h; }
void hid_close(hid_device *h) { free(h); }
int hid_write(hid_device *h, const unsigned char *data, size_t len) {
    if (write(h->fd, data + 1, len - 1) != (ssize_t)(len - 1)) return -1;
    return len;
}
int hid_read(hid_device *h, unsigned char *data, size_t len) {
    struct pollfd p = { h->fd, POLLIN, 0 };
    if (poll(&p, 1, 2000) <= 0) return 0;
    return read(h->fd, data, 8);
}
"""


def need(cmd):
    if shutil.which(cmd) is None:
        raise unittest.SkipTest(cmd + " is not installed")


class Pty(threading.Thread):
    """Pumps a pseudo-terminal into a fake board; `.port` is the slave side, what the tool opens as its serial port."""

    def __init__(self, device):
        super().__init__(daemon=True)
        self.device = device
        self.master, self._slave = pty.openpty()
        self.port = os.ttyname(self._slave)
        self.running = True

    def run(self):
        import select
        while self.running:
            r, _, _ = select.select([self.master], [], [], 0.05)
            if not r:
                continue
            try:
                data = os.read(self.master, 65536)
            except OSError:
                break
            reply = self.device.feed(data)
            while reply:
                n = os.write(self.master, reply)
                reply = reply[n:]

    def close(self):
        self.running = False
        self.join(1)
        os.close(self.master)
        os.close(self._slave)


class KatapultAgainstFlashCan(unittest.TestCase):
    def setUp(self):
        try:
            import serial  # noqa: F401
        except ImportError:
            self.skipTest("pyserial is not installed")
        if not os.path.exists(FLASH_CAN):
            self.skipTest("flash_can.py not found")
        self._t = tempfile.TemporaryDirectory()

    def tearDown(self):
        self._t.cleanup()

    def run_tool(self, board, image):
        path = os.path.join(self._t.name, "klipper.bin")
        with open(path, "wb") as f:
            f.write(image)
        link = Pty(board)
        link.start()
        try:
            # flash_can.py calls asyncio.get_event_loop(), which newer Pythons no longer create by themselves
            runner = ("import asyncio, runpy, sys; asyncio.set_event_loop(asyncio.new_event_loop()); "
                      "sys.argv = ['flash_can.py', '-d', sys.argv[1], '-f', sys.argv[2]]; runpy.run_path(%r, run_name='__main__')" % FLASH_CAN)
            return subprocess.run([sys.executable, "-c", runner, link.port, path], capture_output=True, text=True, timeout=60)
        finally:
            link.close()

    def test_flashes_and_verifies(self):
        image = bytes((i * 7 + 3) & 0xFF for i in range(5000))
        board = fb.FakeKatapult(block=64)
        r = self.run_tool(board, image)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("CAN Flash Success", r.stdout)
        self.assertTrue(board.complete)
        used = image + b"\xff" * (-len(image) % 64)
        self.assertEqual(board.state()["flash_sha1"], hashlib.sha1(used.rstrip(b"\xff")).hexdigest())
        self.assertEqual(board.log[0], "CONNECT")

    def test_other_block_sizes(self):
        for block in (128, 256, 512):
            board = fb.FakeKatapult(block=block)
            r = self.run_tool(board, bytes(range(256)) * 9)
            self.assertEqual(r.returncode, 0, "block %d: %s%s" % (block, r.stdout, r.stderr))

    def test_a_corrupt_flash_fails_the_checksum(self):
        board = fb.FakeKatapult(fault="bad-write")
        r = self.run_tool(board, bytes(range(256)) * 4)
        self.assertNotEqual(r.returncode, 0)
        self.assertTrue(board.complete, "COMPLETE is always sent")


CAN_RUNNER = r"""
import asyncio, os, runpy, socket, sys, threading
sys.path.insert(0, sys.argv[1])
import fake_bootloaders as fb
board = fb.FakeCanBus(uuid=0x112233445566, block=int(sys.argv[4]))
real_socket = socket.socket
a, b = socket.socketpair(socket.AF_UNIX, socket.SOCK_SEQPACKET)

class CanSock(real_socket):
    def bind(self, address):
        pass

def factory(family=-1, type=-1, proto=-1, fileno=None):
    if family == socket.PF_CAN:
        return CanSock(socket.AF_UNIX, socket.SOCK_SEQPACKET, 0, fileno=os.dup(a.fileno()))
    return real_socket(family, type, proto, fileno)

def pump():
    while True:
        try:
            frame = b.recv(64)
        except OSError:
            return
        if not frame:
            return
        for i in range(0, len(frame), 16):
            reply = board.feed(frame[i:i + 16])
            for j in range(0, len(reply), 16):
                b.send(reply[j:j + 16])

threading.Thread(target=pump, daemon=True).start()
socket.socket = factory
asyncio.set_event_loop(asyncio.new_event_loop())
sys.argv = ["flash_can.py", "-i", "can0", "-u", "112233445566", "-f", sys.argv[2]]
try:
    runpy.run_path(sys.argv_flash, run_name="__main__")
finally:
    import json
    open(sys.argv_dump, "w").write(json.dumps(board.state()))
"""


class CanAgainstFlashCan(unittest.TestCase):
    """The CAN bus fake, driven by flash_can.py's own CAN socket path (admin messages, node ids, Katapult over 8-byte frames)."""

    def setUp(self):
        if not os.path.exists(FLASH_CAN):
            self.skipTest("flash_can.py not found")
        self._t = tempfile.TemporaryDirectory()

    def tearDown(self):
        self._t.cleanup()

    def test_flashes_a_node_through_the_admin_protocol(self):
        image = bytes((i * 3 + 7) & 0xFF for i in range(3000))
        path = os.path.join(self._t.name, "klipper.bin")
        dump = os.path.join(self._t.name, "state.json")
        with open(path, "wb") as f:
            f.write(image)
        code = CAN_RUNNER.replace("sys.argv_flash", repr(FLASH_CAN)).replace("sys.argv_dump", repr(dump))
        r = subprocess.run([sys.executable, "-c", code, HERE, path, dump, "64"], capture_output=True, text=True, timeout=120)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("CAN Flash Success", r.stdout)
        import json
        with open(dump) as f:
            st = json.load(f)
        self.assertEqual(st["app"], "katapult")
        self.assertEqual(st["node_id"], 129)
        self.assertTrue(st["complete"])
        padded = image + b"\xff" * (-len(image) % 64)
        self.assertEqual(st["flash_sha1"], hashlib.sha1(padded.rstrip(b"\xff")).hexdigest())
        self.assertEqual(st["admin"][:3], [["admin", 2], ["admin", 18], ["admin", 0]])


class SambaAgainstBossac(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need("g++")
        cls._t = tempfile.TemporaryDirectory()
        cls.bossac = os.path.join(cls._t.name, "bossac")
        srcs = [os.path.join(BOSSAC_SRC, n + ".cpp") for n in BOSSAC_FILES]
        r = subprocess.run(["g++", "-O1", "-w", "-std=gnu++14", "-DVERSION=\"1.9\"", "-o", cls.bossac] + srcs, capture_output=True, text=True)
        if r.returncode != 0:
            raise unittest.SkipTest("bossac does not build here: " + r.stderr[-300:])

    @classmethod
    def tearDownClass(cls):
        cls._t.cleanup()

    def run_tool(self, board, image, extra=("-e", "-b")):
        path = os.path.join(self._t.name, "firmware.bin")
        with open(path, "wb") as f:
            f.write(image)
        link = Pty(board)
        link.start()
        # bossac treats a port as USB by its name: ttyACM*
        name = os.path.join(self._t.name, "ttyACM99")
        if os.path.lexists(name):
            os.unlink(name)
        os.symlink(link.port, name)
        try:
            return subprocess.run([self.bossac, "-U", "-p", name] + list(extra) + ["-w", path, "-v", "-R"], capture_output=True, text=True, timeout=120)
        finally:
            link.close()

    def test_sam3x_erase_write_verify_boot_reset(self):
        image = bytes((i * 13 + 5) & 0xFF for i in range(10 * 1024 + 77))
        board = fb.FakeSamba("sam3x8e")
        r = self.run_tool(board, image)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        st = board.state()
        self.assertTrue(st["erased"] and st["reset"])
        self.assertEqual(st["gpnvm"] & 2, 2, "boot from flash")
        # bossac does not clean its page buffer: after the end of the file the last page holds old data
        self.assertEqual(bytes(board.flash[:len(image)]), image)

    def test_sam4e_single_plane(self):
        image = bytes((i * 3 + 1) & 0xFF for i in range(4096))
        board = fb.FakeSamba("sam4e8e")
        r = self.run_tool(board, image, extra=("-e",))
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(bytes(board.flash[:len(image)]), image)


class HidAgainstHidFlash(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        need("gcc")
        cls._t = tempfile.TemporaryDirectory()
        shim = os.path.join(cls._t.name, "shim.c")
        with open(shim, "w") as f:
            f.write(HID_SHIM)
        cls.tool = os.path.join(cls._t.name, "hid-flash")
        r = subprocess.run(["gcc", "-w", "-std=gnu99", "-I", HIDFLASH_SRC, "-o", cls.tool, os.path.join(HIDFLASH_SRC, "main.c"),
                            os.path.join(HIDFLASH_SRC, "rs232.c"), shim], capture_output=True, text=True)
        if r.returncode != 0:
            raise unittest.SkipTest("hid-flash does not build here: " + r.stderr[-300:])

    @classmethod
    def tearDownClass(cls):
        cls._t.cleanup()

    def run_tool(self, board, image):
        path = os.path.join(self._t.name, "klipper.bin")
        with open(path, "wb") as f:
            f.write(image)
        host, dev = socket.socketpair()

        def pump():
            while True:
                data = b""
                while len(data) < 64:
                    try:
                        more = dev.recv(64 - len(data))
                    except OSError:
                        return
                    if not more:
                        return
                    data += more
                reply = board.report(data)
                if reply:
                    dev.sendall(reply)

        t = threading.Thread(target=pump, daemon=True)
        t.start()
        try:
            return subprocess.run([self.tool, path], capture_output=True, text=True, timeout=120, pass_fds=(host.fileno(),),
                                  preexec_fn=lambda: os.dup2(host.fileno(), 3))
        finally:
            host.close()
            dev.close()

    def test_1k_pages(self):
        image = bytes((i * 11 + 1) & 0xFF for i in range(5000))
        board = fb.FakeHid(page=1024)
        r = self.run_tool(board, image)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        st = board.state()
        self.assertTrue(st["rebooted"])
        self.assertEqual(bytes(board.flash[:len(image)]), image)
        self.assertEqual(st["written"], 5 * 1024)

    def test_2k_pages_are_completed_with_an_extra_sector(self):
        image = bytes((i * 5 + 9) & 0xFF for i in range(3 * 1024 - 100))
        board = fb.FakeHid(page=2048)
        r = self.run_tool(board, image)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(board.state()["written"], 4 * 1024, "the last 2 KiB page is completed")
        self.assertEqual(bytes(board.flash[:len(image)]), image)


if __name__ == "__main__":
    unittest.main()
