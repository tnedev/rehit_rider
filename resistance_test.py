import sys, time
from carol_bike import open_bike, Parser, read_for, PING, START, STOP, set_resistance

s = open_bike(sys.argv[1] if len(sys.argv) > 1 else "/dev/cu.usbserial-110")
time.sleep(1.5); s.reset_input_buffer()
p = Parser()
def show(sec, tag):
    for m in read_for(s, p, sec):
        if m["type"] == "data":
            print(f"[{tag}] rpm={m['rpm']:3d} res={m['resistance']:3d} hr1={m['hr1']:3d} hr2={m['hr2']:3d}  {m['raw']}", flush=True)
        elif m["type"] == "ping":
            print(f"[{tag}] {m}", flush=True)
try:
    for _ in range(2):
        s.write(PING); time.sleep(0.05)
    for _ in range(10):
        s.write(START)
        if any(m["type"] == "data" for m in read_for(s, p, 0.5)):
            break
    show(3, "base")
    for lvl in [int(x) for x in (sys.argv[2] if len(sys.argv) > 2 else "30,60,100,0").split(",")]:
        s.write(set_resistance(lvl))
        print(f"--> sent resistance {lvl}", flush=True)
        show(5, f"L{lvl}")
finally:
    s.write(set_resistance(0)); time.sleep(0.1)
    s.write(STOP); s.close()
