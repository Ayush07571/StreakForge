# StreakForge 🔥

**Code every day. Keep the fire alive.**

StreakForge is a modern, native Android application built with Kotlin, Jetpack Compose, Glance AppWidgets, and WorkManager. It helps developers maintain consistent daily GitHub contribution habits through vivid flame visualizer states, customizable streak rewards, a 6-month DSA topic tracker, and a live home-screen widget.

---

## 🌟 Key Features

- **🔥 Signature Flame Visualizer**:
  - **Lit**: Active glowing flame when you have committed today.
  - **Fading**: Dim flame alerting you to push a commit before the day ends.
  - **Broken**: Burnt wood & ash visual when a streak breaks, motivating a fresh start.
- **📲 Live Glance Home-Screen Widget**:
  - Responsive 2x2 and 4x2 widget sizes showing your current streak, status, and 7-day activity strip.
  - Reads strictly from local cached Room database — **zero background network calls in the widget**.
- **🎁 Self-Set Rewards System**:
  - Unlock customizable rewards at streak milestones (e.g. Day 7, Day 14, Day 30).
  - Fully generic by default and editable per user.
  - Earn **Streak Shields** to protect against accidental single-day breaks.
- **💻 6-Month DSA Tracker**:
  - Structured 26-week curriculum covering 360 essential Data Structures & Algorithms problems.
  - Dynamic pace checking (*Ahead*, *On Pace*, or *Behind*) based on your custom daily target (default: 2 problems/day).
- **🔔 Optional Daily Reminders**:
  - WorkManager background notifications firing at your preferred evening time (default 8:00 PM) only if today's contribution is not yet completed.
- **🔒 Encrypted & Local-First Architecture**:
  - **Zero Backend**: All data flows directly between your phone and `api.github.com`.
  - **Encrypted Storage**: OAuth access tokens are secured on-device using Android Keystore (`EncryptedSharedPreferences`).
  - **Offline First**: Instant startup rendering cached Room data.

---

## 🔒 Privacy & Security

StreakForge is built from the ground up with a privacy-first mindset:

- **Minimal Scope**: Requests only the `read:user` OAuth scope (read-only access to user profile and public/private contribution calendars).
- **No Third-Party Servers**: There are no telemetry, analytics, ad networks, or backend servers.
- **On-Device Storage**: Your data stays on your device. You can export your data as JSON or wipe all data permanently at any time.

For complete privacy details, read our [Privacy Policy](docs/privacy.md).

---

## 📱 Minimum Requirements & Compatibility

- **Minimum Supported OS**: Android 8.0 (API Level 26) or higher.
- **Target OS**: Android 14.0 (API Level 34).
- **Permissions**:
  - `INTERNET`: To fetch contribution calendars directly from GitHub's GraphQL API.
  - `RECEIVE_BOOT_COMPLETED`: To reschedule local widget updates and reminders after device reboot.
  - `POST_NOTIFICATIONS` *(Android 13+)*: Requested **only** if you enable daily reminders.

---

## 🚀 Installation Guide

### Option A: Download Pre-Built APK from GitHub Releases
1. Navigate to the [StreakForge GitHub Releases Page](https://github.com/Ayush07571/StreakForge/releases).
2. Download the latest `app-release.apk`.
3. Open the downloaded `.apk` file on your Android device.
4. If prompted, allow your browser or file manager permission to install applications from this source (*"Install unknown apps"*).
5. Open **StreakForge** and tap **Continue with GitHub** to complete OAuth Device Flow sign-in.

### Option B: Build from Source
```bash
git clone https://github.com/Ayush07571/StreakForge.git
cd StreakForge
./gradlew assembleDebug
```
The compiled debug APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🖼️ App Screenshots

| Home & Flame Visual | Rewards Tracker | DSA Curriculum | Home Widget |
| :---: | :---: | :---: | :---: |
| *(Screenshot Placeholder: Home)* | *(Screenshot Placeholder: Rewards)* | *(Screenshot Placeholder: DSA)* | *(Screenshot Placeholder: Widget)* |

---

## ❓ Frequently Asked Questions (FAQ)

<details>
<summary><b>Why is my streak not updating after I pushed a commit?</b></summary>
<br>
Common reasons include:
1. **GitHub UTC Timezone**: GitHub operates on UTC days (00:00 UTC reset). Check the reset time listed on your home screen.
2. **Commit Email**: Ensure the email address in your git commits matches a verified email address on your GitHub account.
3. **Non-Default Branch**: GitHub only counts contributions pushed to the repository's default branch (e.g. `main` or `master`).
4. **Private Contributions**: If pushing to private repos, verify that *"Private & Fork contributions"* is enabled in your GitHub profile settings.
</details>

<details>
<summary><b>How do I add the live widget to my home screen?</b></summary>
<br>
- **Google Pixel / Stock Android**: Long press any empty space on your home screen -> Widgets -> StreakForge -> Drag to screen.
- **Samsung One UI**: Pinch home screen -> Widgets -> Search StreakForge -> Add.
- **Xiaomi (MIUI / HyperOS)**: Enable "Autostart" and "Display on Lock screen" in Android App Info permissions for StreakForge to allow background widget updates.
</details>

<details>
<summary><b>How does "Check for Updates" work?</b></summary>
<br>
In the **Account** tab, tap **Check for Updates**. StreakForge queries the GitHub Releases API for new published tags. If a new version exists, a dialog offers a direct link to the GitHub Release page. StreakForge never installs updates silently.
</details>

---

## 🛠️ Building & Contributing

StreakForge welcomes contributions! Feel free to open issues or submit pull requests.

```bash
# Run Unit Tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug
```

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
