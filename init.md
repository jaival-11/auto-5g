@AndroidLead @DevOps @UIUX

Read `PROJECT_CONTEXT.md` and `AGENTS.md`. We are starting from a completely empty directory. Your goal is to generate and assemble the complete, functional codebase for **Auto 5G** (`me.jaival.5g`).

Execute the following steps sequentially to build the entire app from scratch:

---

### Step 1: Directory Structure & Git Setup
1. Initialize local git repo.
2. Scaffold directory tree:
   - `app/src/main/java/me/jaival/5g/`
   - `app/src/main/res/`
   - `app/src/github/java/me/jaival/5g/updater/`
   - `app/src/fdroid/java/me/jaival/5g/updater/`

---

### Step 2: Build Scripts & CI Configuration
1. Create `settings.gradle.kts` (Root project name: "Auto 5G").
2. Create project-level `build.gradle.kts`.
3. Create `app/build.gradle.kts` with:
   - Android Compile SDK 35, Min SDK 26, Target SDK 35.
   - Jetpack Compose + Material 3 dependencies (`androidx.compose.material3:material3`, `androidx.compose.animation:animation`, `androidx.navigation:navigation-compose`).
   - DataStore preferences (`androidx.datastore:datastore-preferences`).
   - Shizuku API (`dev.rikka.shizuku:api:13.1.5`, `dev.rikka.shizuku:provider:13.1.5`).
   - Flavor dimensions `distribution`:
     - `github`: `buildConfigField("boolean", "ENABLE_UPDATER", "true")`
     - `fdroid`: `buildConfigField("boolean", "ENABLE_UPDATER", "false")`
4. Create `.github/workflows/build.yml`:
   - Triggers on push to `main`.
   - JDK 17 setup.
   - Runs `./gradlew assembleGithubRelease` and `./gradlew assembleFdroidRelease`.
   - Uploads APK artifacts.
5. Create `fdroid-recipe.yml` stub for reproducible builds.

---

### Step 3: Manifests Setup
1. Create `app/src/main/AndroidManifest.xml`:
   - Declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WRITE_SECURE_SETTINGS`, `PACKAGE_USAGE_STATS`.
   - Declare `Smart5GService` with `foregroundServiceType="specialUse"`.
   - Declare `rikka.shizuku.ShizukuProvider`.
2. Create `app/src/github/AndroidManifest.xml`:
   - Include `<uses-permission android:name="android.permission.INTERNET" />`.
3. Create `app/src/fdroid/AndroidManifest.xml`:
   - Leave clean with NO internet permission.

---

### Step 4: Data Layer & System Controllers
1. **`data/SettingsRepository.kt`**:
   - Uses Preference DataStore to persist:
     - Master Switch State (Boolean)
     - Permission Mode (SHIZUKU_ONETIME, SHIZUKU_CONTINUOUS, MANUAL_ADB)
     - Display Trigger (Enabled, OffDelaySecs, OnDelaySecs)
     - Smart Switching (Enabled, HighMbpsThreshold, LowMbpsThreshold)
     - Whitelist / Blacklist package sets
     - Hotspot Trigger (Enabled, Mode: SMART/ALWAYS, Only5G: Boolean)
     - Updater settings (Enabled, IncludePrereleases, LastCheckedTimestamp)
2. **`system/NetworkModeController.kt`**:
   - Executes `preferred_network_mode` settings changes using `Settings.Global` or Shizuku shell (`Shizuku.newProcess`).
   - Constants for 4G Preferred (9), 5G Preferred (26), and Strict 5G Only (20).
3. **`system/ShizukuManager.kt`**:
   - Checks Shizuku availability, handles permission request callbacks, and executes one-time `pm grant` commands.
4. **`system/TrafficMonitor.kt`**:
   - Coroutine polling `TrafficStats.getMobileRxBytes()` & `getMobileTxBytes()` to compute live Mbps.
5. **`service/Smart5GService.kt`**:
   - Android `ForegroundService` running low-priority notification.
   - Listens to `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`, and `WIFI_AP_STATE_CHANGED`.
   - Evaluates current network state based on Display, Traffic, Whitelist/Blacklist foreground apps, and Hotspot state.

---

### Step 5: Flavor-Specific Updater Logic
1. Interface `me.jaival.5g.updater.UpdateManager`:
   - `fun checkForUpdates(context: Context, force: Boolean)`
2. In `src/github/`: Implement `UpdateManagerImpl` querying GitHub API `jaival-11/auto-5g/releases`, throttling checks to 1 per 4 hours.
3. In `src/fdroid/`: Implement `UpdateManagerImpl` as a no-op stub.

---

### Step 6: Jetpack Compose Material 3 UI Layouts
1. **`ui/theme/Theme.kt`**:
   - Dynamic Material You palette (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
2. **`ui/onboarding/OnboardingScreen.kt`**:
   - Card choices for Setup Method (One-Time Shizuku, Continuous Shizuku, Manual ADB).
   - "Grant Permission" and "Check Status" action buttons with spring physics.
3. **`ui/home/HomeScreen.kt`**:
   - **Top AppBar & Master Switch Card**: Toggle service on/off.
   - **Display Trigger Card**: Switches, Sliders for Off/On delays.
   - **Smart Switching Card**: Switch, status text showing current live speed.
   - **Whitelist/Blacklist Card**: Switch, app selection dialog.
   - **Hotspot Card**: Switch, Segmented Buttons (Smart vs Always), "Only 5G" checkbox.
   - **Updater Card** (GitHub flavor only): Pre-release toggle, "Check Now" button.
4. **`ui/about/AboutScreen.kt`**:
   - App title, version display, GitHub repo link card, bug tracker link card, and license details.
5. **`ui/navigation/AppNavigation.kt`**:
   - `AnimatedContent` navigation between Onboarding, Home, and About screens.
6. **`MainActivity.kt`**:
   - Initializes DataStore, checks setup status, sets up Compose content hierarchy.

---

### Step 7: Final Verification
- Ensure all Kotlin files compile cleanly without missing imports or placeholders.
- Verify that both build flavors (`assembleGithubRelease` and `assembleFdroidRelease`) can be built by Gradle.
- No build is done locally, all builds are done via github actions
