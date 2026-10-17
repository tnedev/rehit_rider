# REHIT Rider

A free, independent Android trainer app for exercise bikes that use the **FTDI USB control board** (the board found in
some CAROL bikes). It talks to the bike directly over USB: no account, no subscription, no internet needed. All your
data stays on the tablet.

> **Not affiliated with, endorsed by or connected to CAROL Bike or Integrated Health Partners Limited.** CAROL is a
> trademark of its owner and is used here only to describe which hardware this app works with. Use at your own risk.

## Features

- **29 workouts**, each with a short summary of the research behind it:
  - **REHIT 2×20**, with a gentle 2×10 → 2×15 on-ramp for new riders, plus 3×20, 6-second micro-sprints and 4×30 sprint intervals
  - **Intervals:** Norwegian 4×4, Seiler 4×8, VO₂max 5×3, 60/30, Rønnestad 30/15, 40/20 microbursts, Tabata, 10×1 min, over-unders, pyramid
  - **Endurance:** zone 2 (30, 45 or 60 min), sweet spot, threshold 2×20, rolling hills, low-cadence tempo, steady and recovery rides
  - **Tests:** ramp FTP test (adapts its step size to the rider) and the 20-minute FTP test
  - **Free ride** with − / + resistance, and **power hold** (ERG: the bike holds your target watts whatever your cadence)
  - **Workout builder** for your own interval sessions
- **Adaptive REHIT sprint load for each rider.** It starts at the research standard (7.5% of body mass, 5% for riders aged 60+) and adjusts after every ride based on your cadence at the end of each sprint.
- **Multiple riders.** "Who's riding?" appears at launch, and each rider has their own profile, history and settings.
- **Live ride screen:** power, cadence, heart rate (from the bike's hand grips or any Bluetooth chest strap), resistance, step countdown, voice coaching and auto-pause.
- **Personalised calories.** They come from the bike's measured work, adjusted for the rider's fitness, blended with the Keytel heart-rate equation and minus resting metabolism (Mifflin-St Jeor). Shown as total and active calories.
- **History and progress:**
  - normalized power, training stress (TSS), heart-rate zones and best efforts
  - fitness/fatigue/form chart (CTL/ATL/TSB)
  - weekly active minutes, streaks, FTP and an estimated VO₂max
- **Health Connect sync** for one rider (the owner of the tablet's Google account). Google Fit and Samsung Health read from Health Connect.
- **Your data:** backup/restore file, CSV export, and TCX export of each ride for Strava, Garmin or TrainingPeaks.
- Light and dark themes.

## Install

1. Download the latest `rehit-rider-*.apk` from [Releases](../../releases).
2. Install it on the bike's tablet (or any Android 8+ tablet with USB-OTG):
   - **Easiest:** copy the APK to the tablet and open it, allowing "install unknown apps" when asked.
   - **With a computer:** enable Developer options (Settings → About tablet → tap *Build number* 7 times), turn on USB debugging, then run `adb install rehit-rider-*.apk`.
3. Plug the bike's USB cable into the tablet and open **REHIT Rider**.
4. When Android asks for USB access, tick **"Use by default for this USB device"** and tap **OK**.
5. Create your rider profile (weight, height, birth year) and start with the recommended workout.

### Tablets locked by a kiosk app

Some bike tablets were set up with a kiosk/device-management app (for example MobiLock) as the *device owner*. This
blocks Developer options and Play Store installs ("consult your admin"), and can dismiss the USB permission dialog.
You can work around it by stopping the kiosk app with `adb shell am force-stop <package>`. The clean fix is a
**factory reset**, which removes the device owner; after that you can install apps normally. Export a backup first if
you already have rides.

## Getting started properly

1. **Health check.** Ask your doctor before doing all-out sprints if you have heart disease, high blood pressure, are
   pregnant, or get chest pain, dizziness or unusual breathlessness. In the original CAROL study, blood pressure
   during sprints reached 218/98 mmHg. Stop if you feel unwell.
2. **Weeks 1–2:** follow the recommended REHIT on-ramp: 2×10 s, then 2×15 s, two or three times a week.
3. **Week 3 onwards:** REHIT 2×20 two or three times a week. The sprint load adapts automatically.
4. After a few rides, take the **ramp FTP test**. It sets the targets of every ERG interval workout.
5. Add one longer session a week (zone 2, sweet spot or 4×4) for an all-round programme.

## Power accuracy

Power is computed from a 256×256 lookup table (cadence × resistance) corrected with one bike's calibration factors.
It matched all 68,357 logged samples from the original app for that bike exactly. Other bikes with the same board
should be close, but individual calibration can shift watts by a few percent. The table and calibration factors are
in [`ride/app/src/main/assets/power_table.bin`](ride/app/src/main/assets) and [`power_model.py`](power_model.py).

## Bike protocol

FTDI FT232, 115200 baud, 8N1, no flow control. **Keep DTR and RTS deasserted**, because they reset the board into its
bootloader.

| Direction | Bytes | Meaning |
|---|---|---|
| tablet → bike | `44 01` | ping (send twice, 50 ms apart) |
| tablet → bike | `49 01` / `49 00` | start / stop the data stream |
| tablet → bike | `64 nn` | set resistance, 0–255 (the app uses 0–220; wait 2 s between changes, 4 s after setting 0) |
| bike → tablet | `7B` serial[8] fw[2] state | ping reply |
| bike → tablet | `39` res period[2] hr1[2] hr2[2] xor | data, big-endian. rpm = 60000 / period / 4; hr = 60000 / hr_period; xor = XOR of bytes 0–7 |

[`carol_bike.py`](carol_bike.py) is a small Python client for testing from a computer. It's best run on the tablet's
USB port, because the resistance motor didn't respond when the board was connected to a Mac.

## Building

The Android project is in [`ride/`](ride). It needs JDK 17, Android SDK 36 and Gradle 8.12.1 (or just open it in Android
Studio):

```bash
cd ride && gradle assembleRelease
```

## Research references

The workouts follow the following research:

- Metcalfe 2012
- Vollaard 2017
- Hutchinson 2026 (meta-analysis of 93 sprint-interval trials)
- Nalçakan 2018
- Thomas 2020
- Cuddy 2019 (CAROL REHIT trial)
- Gillen 2016
- Helgerud 2007
- Seiler 2013
- Wen 2019
- Rønnestad 2015
- Millet 2003
- Tabata 1996
- Little 2010
- Storoschuk 2025
- Borszcz 2019
- Keytel 2005

Each workout's screen in the app lists the studies it is based on. This app is not medical advice.
