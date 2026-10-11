# STEAM BATTLESHIP — Android APK

This Android application packages **STEAM BATTLESHIP by MrDhameri** as an Android WebView app.

## How it works
- Full-screen landscape gameplay and an app launcher icon.
- Opens the latest live game from https://mohammaddhameri-blip.github.io/stem-battleship/.
- If the live game is unreachable, opens an offline game copy bundled in the APK.
- Offline mode supports same-device play, the 325-question bank, ship customisation, original music, and SFX. Online multiplayer needs an Internet connection to load PeerJS signaling.
- Ship settings use WebView local storage. Online and offline copies have different origins and separate browser storage.
- Android back navigates through the game; it asks for confirmation before leaving an active battle.

## Build
The GitHub Actions workflow at `.github/workflows/android-apk.yml` automatically builds a **debug-signed test APK** when the Android sources are changed.

The built APK is available under the GitHub Actions run's **Artifacts** section and, if the publishing step succeeds, through the stable **android-latest** GitHub Release.

Note: Debug-signed builds are intended for personal/classroom sideloading and testing, not publishing to Google Play. A release build would require a securely stored private signing key. If a debug signing key is ever regenerated, uninstalling the earlier build may be required before updating it.

## Requirements
- Android 8.0 or later (API 26+).
- Android System WebView enabled and reasonably up to date.
- Install permission for APKs from your browser/files app when sideloading.
- Internet is needed for online multiplayer and the latest hosted version; offline copy works locally.

No advertising SDK, user tracking, or analytics library is included in the Android wrapper.
