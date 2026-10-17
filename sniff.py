import serial, sys, time
port = sys.argv[1] if len(sys.argv) > 1 else "/dev/cu.usbserial-110"
for baud in [9600, 19200, 38400, 57600, 115200, 4800, 230400]:
    with serial.Serial(port, baud, timeout=0.2) as s:
        s.reset_input_buffer()
        t = time.time(); buf = b""
        while time.time() - t < 3:
            buf += s.read(512)
        print(f"{baud}: {len(buf)} bytes  hex={buf[:64].hex(' ')}  ascii={buf[:64]!r}")
