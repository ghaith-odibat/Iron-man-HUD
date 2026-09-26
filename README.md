# Iron HUD

An Android camera app for Samsung tablets that turns the camera into an Iron Man style HUD.
Point it at anything; J.A.R.V.I.S. locks on and briefs you about what it is.

Everything is drawn in a single colour, light tech blue (`#66D9FF`).

## Features

* **Instant on-device tracking:** ML Kit detects and tracks up to 5 objects per frame, on the
  device, with no network needed. Every object gets HUD brackets straight away.
* **Auto-lock + tap:** the object under the centre reticle is identified after 0.8 s. You can also
  tap any object, or empty space (landmarks, screens) to scan that area. **SCAN** checks the reticle.
* **Fast AI briefs:**
  * The app first shows an offline guess (`PRELIM: Cup 87%`) in about 50 ms.
  * It then sends a small crop of the object to a free vision model, and the brief streams into
    the panel as it arrives.
  * The brief has the name, category, confidence, a 2-sentence summary, key facts and a
    wireframe of the target.
  * Each object is cached, so it is never queried twice.
* **Automatic key rotation:** add as many free keys as you want (Gemini, Groq, OpenRouter). A key
  that hits its limit is benched until it resets, and the same scan is retried on the next key
  straight away.
* **J.A.R.V.I.S. voice:** briefs can be read aloud with the tablet's built-in TTS, free and offline.
* **Hologram tint:** renders the camera feed itself in HUD blue.
* **SNAP:** saves the camera frame plus the HUD overlay to `Pictures/IronHUD`.
* **HUD chrome:** compass tape from the rotation sensor, artificial horizon, clock, battery,
  uplink status and a scan sweep.

## Install on your tablet

1. On the tablet, open **Releases → latest** in this repo and download `IronHUD.apk`.
2. Open the file. If Android asks, allow *Install unknown apps* for your browser or *My Files*.
3. Launch **Iron HUD** and allow camera access.
4. Tap **VAULT** and add at least one free key. See **[docs/GET_FREE_KEYS.md](docs/GET_FREE_KEYS.md)**.

Updates install over the previous version, because every build is signed with the same sideload
key.

## Controls

| Control | Action |
|---|---|
| Hold an object in the reticle | Auto-lock and identify (when **AUTO** is on) |
| Tap an object / area | Identify it |
| **SCAN** | Identify what is under the reticle now |
| **RESCAN** / **✕** on the card | Query again / close the card |
| **VOICE**, **TINT**, **AUTO** | Toggle voice read-out, hologram tint, auto-lock |
| **SNAP** | Save a screenshot with the HUD |
| **VAULT** | Manage API keys, provider order, models |

## How it works

```
CameraX preview ──► ML Kit object tracker (on-device, every frame) ──► brackets / auto-lock
                                   │ lock
                                   ▼
             crop + 512 px JPEG ──► ML Kit labeler (instant offline guess)
                                   │
                                   ▼
        KeyPool ─► Gemini (stream) ─429─► next key ─► Groq ─► OpenRouter
                                   │
                                   ▼
                  BriefParser (progressive) ──► HUD card + voice
```

Code map: `ai/` (providers, key rotation, streaming, parsing; plain JVM, unit-tested),
`vision/` (crop, wireframe, labeler), `ui/` (Compose HUD), `data/` (encrypted key vault and
settings).

## Build

CI (`.github/workflows/build-apk.yml`) runs the unit tests and builds the release APK on every push,
then publishes it as a GitHub Release. To build locally with JDK 17+ and the Android SDK:

```
./gradlew testDebugUnitTest assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

`keystore/ironhud-sideload.jks` is a **non-secret** signing key committed on purpose, so sideloaded
updates install cleanly. Don't use it for anything published to a store.
