# REHIT Rider

A free Android app for exercise bikes with the FTDI USB control board, as found in some CAROL bikes. It connects to the
bike over USB, with no account or subscription.

- REHIT with an adaptive sprint load, plus interval, endurance and FTP-test workouts, and a builder for your own
- Several rider profiles, each with their own history, progress and personalised calories
- Heart rate from the bike's hand grips or any Bluetooth strap
- Optional sync to each rider's phone (Google Fit / Samsung Health)

## Install

1. Download `rehit-rider-*.apk` from [Releases](../../releases) and install it on the bike's tablet.
2. Plug in the bike's USB cable, open **REHIT Rider**, and allow USB access (tick "Use by default").

Tablets locked by a kiosk app (for example MobiLock) may block installs. A factory reset removes the kiosk.

## Phone sync (optional)

1. Install `rehit-rider-sync-*.apk` on your Android phone. It needs Health Connect.
2. On the tablet: **Settings → Sync to my phone → Link a phone**, then enter the code in the phone app. Both devices must be on the same Wi-Fi.

Each phone only receives the rides of the rider it was linked to.

## Build

Open `ride/` in Android Studio, or run `gradle assembleRelease`. It needs JDK 17, SDK 36 and Gradle 8.12.1.

## Disclaimer

Provided as is, without warranty of any kind. Use at your own risk. This is not medical advice: check with a doctor
before hard exercise and stop if you feel unwell.

Not affiliated with or endorsed by CAROL Bike or Integrated Health Partners Limited. CAROL is a trademark of its owner.
