"""Carol bike serial protocol (reverse-engineered from com.sportics.horbikeapplication 3.6.28).

Link: FTDI FT232, 115200 8N1, no flow control.

Host -> bike:
  44 01  'D',1   ping (bike answers with a 12-byte ping packet)
  49 01  'I',1   start streaming data packets
  49 00  'I',0   stop
  64 nn  'd',n   set resistance level n (app waits 2s, or 4s after level 0, before the next change)

Bike -> host:
  7B serial[8] fw[2] state[1]            ping reply (state 11 = "logged in")
  39 res period[2] hr1[2] hr2[2] xor     data, all big-endian; xor = XOR of bytes 0..7
     rpm = 60000/period/4, hr = 60000/hr_period (0 means no reading)
"""
import sys
import time

import serial

PING = b"\x44\x01"
START = b"\x49\x01"
STOP = b"\x49\x00"


def set_resistance(level: int) -> bytes:
    return bytes([0x64, level & 0xFF])


def _bpm(period: int) -> int:
    return 60000 // period if period else 0


class Parser:
    def __init__(self):
        self.buf = bytearray()

    def feed(self, data: bytes):
        self.buf += data
        while self.buf:
            head = self.buf[0]
            if head == 0x39:
                if len(self.buf) < 9:
                    return
                pkt = bytes(self.buf[:9])
                x = 0
                for b in pkt[:8]:
                    x ^= b
                if x != pkt[8]:
                    del self.buf[0]
                    continue
                del self.buf[:9]
                period = int.from_bytes(pkt[2:4], "big")
                yield {
                    "type": "data",
                    "resistance": pkt[1],
                    "rpm": int(60000 / period / 4) if period else 0,
                    "hr1": _bpm(int.from_bytes(pkt[4:6], "big")),
                    "hr2": _bpm(int.from_bytes(pkt[6:8], "big")),
                    "raw": pkt.hex(" "),
                }
            elif head == 0x7B:
                if len(self.buf) < 12:
                    return
                pkt = bytes(self.buf[:12])
                del self.buf[:12]
                yield {
                    "type": "ping",
                    "serial": int.from_bytes(pkt[1:9], "big"),
                    "firmware": int.from_bytes(pkt[9:11], "big"),
                    "state": pkt[11],
                    "raw": pkt.hex(" "),
                }
            else:
                yield {"type": "unknown", "raw": f"{head:02x}"}
                del self.buf[0]


def open_bike(port: str) -> serial.Serial:
    # DTR/RTS are wired to the board's reset/bootloader lines (the app's firmware flasher toggles them).
    # Keep both deasserted from the moment the port opens, as the app does, or the MCU sits in its
    # bootloader and only answers "TB Ver1.0".
    s = serial.Serial()
    s.port = port
    s.baudrate = 115200
    s.timeout = 0.05
    s.dtr = False
    s.rts = False
    s.open()
    return s


def read_for(s: serial.Serial, parser: Parser, seconds: float):
    end = time.time() + seconds
    while time.time() < end:
        for msg in parser.feed(s.read(256)):
            yield msg


def main():
    port = sys.argv[1] if len(sys.argv) > 1 else "/dev/cu.usbserial-110"
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 15
    s = open_bike(port)
    time.sleep(1.5)  # let the MCU boot out of reset into its application firmware
    s.reset_input_buffer()
    p = Parser()
    try:
        for _ in range(2):
            s.write(PING)
            time.sleep(0.05)
        for msg in read_for(s, p, 0.5):
            print(msg)
        for attempt in range(3):
            s.write(START)
            got = False
            for msg in read_for(s, p, 0.5):
                print(msg)
                got = got or msg["type"] == "data"
            if got:
                break
            print(f"start attempt {attempt + 1}: no data yet")
        for msg in read_for(s, p, seconds):
            if msg["type"] == "data":
                print(f"rpm={msg['rpm']:4d} res={msg['resistance']:3d} hr1={msg['hr1']:3d} hr2={msg['hr2']:3d}  [{msg['raw']}]")
            else:
                print(msg)
    finally:
        s.write(STOP)
        s.close()


if __name__ == "__main__":
    main()
