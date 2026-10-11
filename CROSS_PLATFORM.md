# Cross-platform STEAM Battleship

**Android and iPhone players use the same online room**, with the same six-character code.
There is no separate iPhone backend or special multiplayer format.

## iPhone / iPad
1. Open **https://mohammaddhameri-blip.github.io/stem-battleship/** in **Safari**.
2. Optionally tap Safari's **Share** icon and **Add to Home Screen** to install the web app.
3. Open the game, go to **ONLINE** and enter the room code from an Android, iPhone, or PC host.
4. Rotate to landscape for a larger battlefield.

## Android
Install the latest [APK](https://github.com/mohammaddhameri-blip/stem-battleship/releases/download/android-latest/STEAM-Battleship.apk), or use the same website in Chrome.
APK users connect to iPhone Safari users through the existing PeerJS WebRTC data-channel multiplayer.

## Desktop
Open the same URL in a modern browser.

## Important operational limitations
- All online players need an internet connection, and the host needs to keep the game open.
- Browser networking depends on WebRTC connectivity; strict firewalls, VPNs, private relays or restrictive networks can prevent some peer connections. This is browser-based peer-to-peer gameplay and does not include a relay/TURN service.
- On iPhone, **Add to Home Screen** makes an installable PWA. It is not a signed App Store IPA.
- Same-device practice and the question bank can work offline after the first successful PWA install/cache, but online matches cannot.
- Standalone PWA local storage may be separate from Safari or the Android app; each install keeps its own ship colours and names.
- A six-character code remains the easiest way to join across browsers when a shared link opens in a different browser than the installed PWA.
