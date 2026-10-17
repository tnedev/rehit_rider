"""
Re-implementation of the CAROL bike app's power / calorie model
(decompiled from com.android.feature.bike.controller.impl.bike.lookupTables.*,
 lb.PowerCorrectionAndEmittingUseCase, dg.PowerTelemetryHelper).

Usage:
    python3 power_model.py            # runs validation against tablet_logs/logs/*.log
"""
import glob
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
POWER_CURVE = os.path.join(HERE, "apk/src/resources/assets/power_curve.txt")
LOG_DIR = os.path.join(HERE, "tablet_logs/logs")

# Per-bike calibration as stored on the tablet (BikeCalibration, logged as
# "calfactor1..4"/"intercept"/"gradientFactorK/P/C" in the 2026 logs).
USER_CALIBRATION = dict(
    intercept=0.3407914,
    g1=-0.006478,   # * innate
    g2=8.139e-4,    # * rpm
    g3=2.79e-5,     # * innate^2
    g4=6.9e-6,      # * rpm^2
    K=0.772093591, P=1.045523623, C=6.40628568,  # innate -> standard resistance
)
ZERO_CALIBRATION = dict(intercept=0.0, g1=0.0, g2=0.0, g3=0.0, g4=0.0, K=0.0, P=0.0, C=0.0)


def load_power_table(path=POWER_CURVE, cal=USER_CALIBRATION):
    """table[rpm][innate]  (256x256).  File line k -> table[k % 256][k // 256],
    i.e. line index = innate*256 + rpm.  Values < 1.0 are set to 0. The file
    has 65535 lines, so table[255][255] stays 0."""
    t = [[0.0] * 256 for _ in range(256)]
    with open(path) as f:
        for k, line in enumerate(f):
            v = float(line)
            t[k % 256][k // 256] = 0.0 if v < 1.0 else v
    # PowerCalculateUseCase.fillPowerLookupCalibrated
    for rpm in range(256):
        for r in range(256):
            p = t[rpm][r]
            if p > 0.0:
                t[rpm][r] = (cal["g4"] * rpm ** 2 + cal["g3"] * r ** 2 + cal["g2"] * rpm
                             + cal["g1"] * r + math.sqrt(p) + cal["intercept"]) ** 2
    return t


_TABLE = None


def power_watts(rpm, innate_resistance, table=None):
    """Instantaneous power (W). rpm and innate resistance are integers (& 0xFF)."""
    global _TABLE
    if table is None:
        if _TABLE is None:
            _TABLE = load_power_table()
        table = _TABLE
    return table[int(rpm) & 0xFF][int(innate_resistance) & 0xFF]


def rpm_from_period(period_ms):
    """gh.C3655a: (short)((60000 / period) / 4.0f) -- integer division first."""
    return 0 if period_ms == 0 else int((60000 // period_ms) / 4.0)


def standard_resistance(innate, cal=USER_CALIBRATION):
    """kb.ResistanceCalculateUseCase.getResistance (display resistance)."""
    if cal["K"] == 0 or cal["P"] == 0:      # areGradientsCorrect == false
        return innate                        # Resistance(i): standard == innate
    if innate <= 0:
        return 0    # app builds resistances from standard via inverse; std 0 -> NaN -> innate 0
    if innate > 220:
        return 220
    v = cal["C"] + innate ** cal["P"] * cal["K"]
    return 0 if v < 0 else (255 if v > 255 else int(math.floor(v + 0.5)))


def calories(power_per_second):
    """kcal: every 1-s clock tick adds 1.2 * P / 1000 (P = latest emitted power)."""
    return sum(1.2 * p / 1000.0 for p in power_per_second)


def calories_from_total_power(total_power_joules):
    return 0.0012 * total_power_joules


# ----------------------------------------------------------------- validation
RE_EMIT = re.compile(r"calculateAndEmitPower: rpm: (-?\d+); defaultPower: ([\d.Ee+-]+) "
                     r"\(correctedPower: ([\d.Ee+-]+)\) defaultResistance: Resistance\("
                     r"innateResistance=(\d+), standardResistance=(\d+)\) "
                     r"\(correctedInnateResistance: (\d+)\)")
RE_TICK = re.compile(r"clock task \d+: ExtendedTelemetryPerSecond\(clock=(\d+),.*?duration=(\d+),.*?"
                     r"totallyPowerValue=([\d.Ee+-]+), powerValue=([\d.Ee+-]+), averagePowerValue=([\d.Ee+-]+),"
                     r".*?caloriesValue=([\d.Ee+-]+)")
RE_UPLOAD = re.compile(r"syncUploadRide .*?(\{\"appVersion\".*\})")


def validate():
    logs = sorted(glob.glob(os.path.join(LOG_DIR, "*.log")))
    n = n_bad = 0
    max_err = 0.0
    corr_n = corr_bad = 0
    std_n = std_bad = 0
    samples = {}
    ticks = []
    uploads = []
    for path in logs:
        with open(path, errors="replace") as f:
            for line in f:
                m = RE_EMIT.search(line)
                if m:
                    rpm, dp, cp, inn, std, cinn = (int(m[1]), float(m[2]), float(m[3]),
                                                   int(m[4]), int(m[5]), int(m[6]))
                    pred = power_watts(rpm, inn)
                    err = abs(pred - dp)
                    n += 1
                    max_err = max(max_err, err)
                    if err > 1e-6:
                        n_bad += 1
                    if cinn != inn:
                        corr_n += 1
                        if abs(power_watts(rpm, cinn) - cp) > 1e-6:
                            corr_bad += 1
                    std_n += 1
                    if standard_resistance(inn) != std:
                        std_bad += 1
                    samples.setdefault((rpm, inn), (dp, pred))
                    continue
                m = RE_TICK.search(line)
                if m:
                    ticks.append(tuple(float(x) for x in m.groups()))
                    continue
                m = RE_UPLOAD.search(line)
                if m:
                    try:
                        uploads.append(json.loads(m[1]))
                    except ValueError:
                        pass

    print(f"Instantaneous power: {n} logged samples, {n_bad} mismatches (>1e-6 W), "
          f"max abs error {max_err:.3g} W")
    print(f"  resistance-change-corrected samples: {corr_n}, mismatches using corrected innate: {corr_bad}")
    print(f"  innate->standard resistance mismatches: {std_bad}/{std_n}")
    print("  rpm innate   logged_W        predicted_W")
    for key in sorted(samples, key=lambda k: (k[1], k[0]))[::max(1, len(samples) // 12)][:12]:
        dp, pred = samples[key]
        print(f"  {key[0]:3d} {key[1]:6d}   {dp:14.9f}  {pred:14.9f}")

    if ticks:
        ce = max(abs(calories_from_total_power(t[2]) - t[5]) for t in ticks)
        ae = max(abs(t[2] / t[1] - t[4]) for t in ticks if t[1] > 0)
        print(f"Per-second telemetry: {len(ticks)} ticks; max |0.0012*totalPower - calories| = {ce:.3g} kcal; "
              f"max |totalPower/duration - avgPower| = {ae:.3g} W")
    if uploads:
        print(f"Ride uploads: {len(uploads)}")
        for u in uploads[:5]:
            print(f"  duration {u['duration']:5d}s  calories {u['calories']:.4f}  "
                  f"pred {calories_from_total_power(u['totalPower']):.4f}  "
                  f"avgP {u['averagePower']:.3f} pred {u['totalPower'] / u['duration']:.3f}")
        ce = max(abs(calories_from_total_power(u["totalPower"]) - u["calories"]) for u in uploads)
        print(f"  max calories error over all uploads: {ce:.3g} kcal")


if __name__ == "__main__":
    validate()
    sys.exit(0)
