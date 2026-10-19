"""A pretend Klipper MCU on a pseudo-terminal, for testing `fwcheck.py probe`.

It speaks just enough of the Klipper serial protocol (framing, sequence
numbers, CRC, the `identify` handshake) to hand out a data dictionary, using the
real klippy/msgproto.py. Used by test_fwcheck.py, and handy by hand:

    python3 fake_mcu.py firmware/output/<profile>-<engine>.dict
    -> prints the pty path; give it to "Verify Flashed Firmware" (type it by hand).
"""
import importlib.util
import json
import os
import pty
import sys
import threading
import time
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
MSGPROTO = os.path.join(HERE, "..", "..", "main", "klipper", "klippy", "msgproto.py")


def load_msgproto(path=MSGPROTO):
    spec = importlib.util.spec_from_file_location("fake_mcu_msgproto", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class FakeMCU(threading.Thread):
    def __init__(self, dictionary, msgproto_path=MSGPROTO):
        super().__init__(daemon=True)
        self.mp = load_msgproto(msgproto_path)
        self.parser = self.mp.MessageParser()
        self.identify = zlib.compress(json.dumps(dictionary).encode())
        self.master, slave = pty.openpty()
        self.port = os.ttyname(slave)
        self._slave = slave  # keep it open so the line stays alive
        self.next_seq = 0
        self.running = True

    def _send(self, payload):
        flat = []
        for x in self.parser.encode_msgblock(self.next_seq, payload):
            flat.extend(x if isinstance(x, list) else [x])
        os.write(self.master, bytes(flat))

    def run(self):
        buf = bytearray()
        import select
        while self.running:
            if not select.select([self.master], [], [], 0.1)[0]:
                continue
            try:
                buf += os.read(self.master, 256)
            except OSError:
                return
            while buf:
                if buf[0] == self.mp.MESSAGE_SYNC:
                    del buf[0]
                    continue
                n = self.parser.check_packet(bytes(buf))
                if n == 0:
                    break
                if n < 0:
                    del buf[:1]
                    continue
                pkt = bytes(buf[:n])
                del buf[:n]
                seq = pkt[self.mp.MESSAGE_POS_SEQ] & self.mp.MESSAGE_SEQ_MASK
                if seq != self.next_seq:      # wrong sequence: tell the host what we expect
                    self._send([])
                    continue
                self.next_seq = (self.next_seq + 1) & self.mp.MESSAGE_SEQ_MASK
                if n > self.mp.MESSAGE_MIN:
                    p = self.parser.messages_by_id.get(pkt[2])
                    if p is not None and p.name == "identify":
                        params = self.parser.parse(pkt)
                        chunk = self.identify[params["offset"]:params["offset"] + params["count"]]
                        self._send(self.parser.create_command(
                            "identify_response offset=%d data=%s" % (params["offset"], chunk.hex())))
                        continue
                self._send([])                 # plain ack

    def stop(self):
        self.running = False


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as f:
        mcu = FakeMCU(json.load(f))
    mcu.start()
    print("fake MCU on", mcu.port, flush=True)
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
