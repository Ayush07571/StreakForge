# Privacy Policy for StreakForge

*Last Updated: October 6, 2026*

**StreakForge** ("the Application") is a local-first, native Android application designed to help users track their daily GitHub contribution streaks. We respect your privacy and are committed to protecting it.

---

## 1. Zero Backend Architecture

StreakForge operates entirely without external servers, custom databases, or backend services managed by us.

- **Direct Communication**: All network communications occur strictly between your Android device and GitHub's official APIs (`api.github.com`).
- **No Third-Party Analytics**: StreakForge does not use any analytics services, telemetry toolkits, crash reporters, or tracking SDKs.
- **No Ads**: StreakForge is completely free of advertising and advertising identifiers.

---

## 2. Information Collected and Processed

### A. Authentication & Tokens
- StreakForge utilizes **GitHub OAuth Device Flow** to authenticate your GitHub account without requiring you to share your GitHub password.
- The app requests only the minimal **`read:user`** scope, granting read-only access to public profile data and contribution calendars.
- Your OAuth access token is encrypted on-device using **Android Keystore** and stored in `EncryptedSharedPreferences`.
- Tokens are **never logged**, transmitted to third parties, or uploaded to any server other than GitHub for API requests.

### B. Contribution & Profile Data
- StreakForge queries GitHub's GraphQL API to retrieve your public/private contribution calendar counts, username, avatar URL, bio, follower count, and public repository counts.
- This data is stored locally on your device using an encrypted/isolated **Room SQLite database**.

### C. Personalization & App Data
- Your DSA tracker progress, custom reward titles/emojis, claimed reward states, streak shield inventory, and daily reminder preference settings are stored strictly in local device storage.

---

## 3. Data Storage & Security

- **Encryption**: Sensitive credentials (access tokens) are encrypted using hardware-backed key storage via Android Keystore (`androidx.security.crypto`).
- **Backup Exclusion**: Room database files and encrypted token preferences are explicitly excluded from Android auto-backups (`allowBackup="false"`) to prevent sensitive authentication data from syncing to cloud backups.

---

## 4. Notifications & WorkManager

- If enabled, optional daily reminders use Android's **WorkManager** to schedule local checks at your chosen evening time.
- StreakForge checks local cached data to see if you have pushed a contribution today. No network call is made to trigger notifications.
- You can revoke notification permissions at any time via Android System Settings.

---

## 5. Home-Screen Widget

- The **StreakForge Glance AppWidget** reads contribution data exclusively from your device's local Room database cache.
- The widget **never initiates network requests**.

---

## 6. Your Rights & Control

You retain full control over your data in StreakForge:

- **Export Data**: In the **Account** tab, you can tap **Export My Data (JSON)** to view and copy all stored local contribution data, DSA progress, and rewards history.
- **Sign Out**: Tapping **Sign Out** revokes the active session, deletes local authentication credentials from Android Keystore, cancels scheduled background workers, and resets the home screen widget.
- **Delete Data**: Tapping **Delete My Data** permanently wipes all local database tables, settings, and credentials associated with your account from your device.
- **Revoke OAuth**: You can revoke StreakForge's OAuth access at any time directly through your [GitHub Authorized Applications Settings](https://github.com/settings/applications).

---

## 7. Changes to This Privacy Policy

If we update this Privacy Policy, the updated document will be published at this location (`docs/privacy.md`) in the official StreakForge GitHub repository.

---

## 8. Contact & Issues

If you have questions or concerns regarding this Privacy Policy or StreakForge's data handling practices, please open an issue on the official [StreakForge GitHub Repository](https://github.com/Ayush07571/StreakForge/issues).
