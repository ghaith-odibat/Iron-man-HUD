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
* **Zoom:** pinch anywhere, or use the − / + strip at the bottom, to zoom up to the camera's maximum.
  Tap the readout to jump back to 1×.
* **LIGHT:** switches the tablet's flashlight on or off for scanning in the dark.
* **SNAP:** saves the camera frame plus the HUD overlay to `Pictures/IronHUD`.
* **HUD chrome:** compass tape from the rotation sensor, artificial horizon, clock, battery,
  uplink status and a scan sweep.

## Plane Mode ✈

Tap **PLANE** to see live aircraft through the camera:

* **3D holograms:** every aircraft within ~50 km is drawn as a blue wireframe model. The model
  (airliner, wide-body, four-engine, business jet, light aircraft, helicopter) is picked from its
  type. It is rotated to the plane's real heading and climb, and seen from where you stand, so a
  plane overhead shows its belly. Models glide smoothly between data updates.
* **Tags:** each plane is labelled with callsign, type, flight level or altitude, speed and
  distance. Planes outside the view get arrows on the screen edge.
* **Radar:** a heading-up scope (bottom left) shows everything around you, with rings at
  10/25/50 km and your camera's field of view.
* **Tap a plane:** a card opens with airline, aircraft type, registration, altitude, speed,
  heading, vertical speed, distance, bearing and squawk. It includes a spinning 3D model, and
  J.A.R.V.I.S. adds an AI brief (using your Vault keys; cached).
* **Live data:** adsb.lol, adsb.fi, then the OpenSky Network. They are free, need no key, and the
  app switches automatically if one fails. Data © adsb.lol (ODbL), adsb.fi and The OpenSky
  Network, for personal, non-commercial use.
* **Needs:** location permission (asked the first time), GPS and internet.
* **Accuracy:** where planes appear depends on the tablet's compass (typically ±5–15°). The status
  line shows `HDG ±N°`. If it asks you to calibrate, wave the tablet in a figure 8 away from metal
  and magnets.

## Install on your tablet

1. On the tablet, open **Releases → latest** in this repo and download one of these:
   * `IronHUD-arm64.apk` (~24 MB) for any 64-bit tablet, which covers every Galaxy Tab S
     and Tab A from recent years.
   * `IronHUD.apk` (~31 MB, universal) if the arm64 one won't install, e.g. an older 32-bit
     tablet.
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
| **LIGHT** | Turn the device flashlight (camera torch) on/off |
| **PLANE** | Plane Mode: live aircraft as 3D holograms + radar; tap a plane for its card |
| Pinch with two fingers / **−** **+** | Zoom out / in (tap the ZOOM readout to go back to 1×) |
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
./gradlew testUniversalDebugUnitTest assembleRelease
# → app/build/outputs/apk/{universal,arm64}/release/*.apk
```

`keystore/ironhud-sideload.jks` is a **non-secret** signing key committed on purpose, so sideloaded
updates install cleanly. Don't use it for anything published to a store.
