# Project Context: Auto 5G

## General Info
- **App Name**: Auto 5G
- **Package Name**: me.jaival.5g
- **Repository**: https://github.com/jaival-11/auto-5g
- **Architecture**: Single Activity, pure Jetpack Compose, Material 3 Expressive, Smooth Animations.
- **Flavors**: 
  - `github` (Normal version with in-app updater)
  - `fdroid` (F-Droid version, STRICTLY no internet permission in manifest, no updater logic).

## Core Definition
"Switching to 5G" means setting the network to **5G Preferred** (NR/LTE/GSM/WCDMA) so the user maintains connectivity if 5G is unavailable. It does NOT mean "5G Only" (except for an explicit option inside the Hotspot feature). "Switching to 4G" means setting to 4G/LTE Preferred.

## Onboarding / First Install Flow
1. Welcome screen using Jetpack Compose.
2. Ask for `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` permissions.
3. Ask user to grant `WRITE_SECURE_SETTINGS` via one of three methods:
   - Quick One-Time Shizuku Grant (Grants native permission permanently via Shizuku shell).
   - Continuous Shizuku Mode (Uses Shizuku IPC for every toggle).
   - Manual ADB (Provides the exact `adb shell pm grant...` command to copy).
4. Once permissions are verified, navigate to the Home Page.

## Home Page (Dashboard)
- **Master Switch**: At the very top to enable/disable the background foreground service entirely.
- **Layout**: A vertical scrollable list of Feature Cards. Every feature card has an individual on/off toggle.

### Feature 1: Display Trigger
- **Display Off Delay**: Slider/input for seconds to wait after screen turns off before switching to 4G.
- **Display On Delay**: Slider/input for seconds to wait after screen turns on before switching to 5G.

### Feature 2: Smart Switching (Traffic Monitor)
- Monitors `TrafficStats` throughput.
- **Logic**: If bandwidth requested is high (streaming/downloading), switch to 5G. If low (messaging/light reading), stay on 4G.
- **Screen-Off Exception**: If the display is off but heavy internet consumption is detected (e.g., background downloads), switch to/stay on 5G.

### Feature 3: Whitelist/Blacklist
- Select apps from installed packages.
- **Whitelist**: When these apps are in the foreground/running/opening, always force 5G.
- **Blacklist**: When these apps are in the foreground/running/opening, always force 4G.

### Feature 4: Hotspot Detection
Detects active Tethering/Hotspot (`WIFI_AP_STATE_CHANGED`).
- **Mode Toggle (Radio buttons/Segmented control)**:
  1. **Smart Switching Mode**: Analyzes client device throughput. High demand -> 5G, Low demand -> 4G.
  2. **Always Mode**: Automatically switches to 5G when the hotspot turns on. 
     - *Sub-option*: Checkbox for "Only 5G" (Strict 5G mode, drops 4G fallback).

### Feature 5: Updater (GITHUB FLAVOR ONLY)
- Automatically checks `jaival-11/auto-5g` GitHub releases on app open.
- Throttled to a maximum of 1 check every 4 hours.
- Toggle switch: "Enable Pre-releases" (includes alpha/beta tags in check).

## About Page
- Simple Material 3 screen.
- Displays developer profile, GitHub Repo link, App Version, Issue Tracker link, and License (GPL-3.0).

