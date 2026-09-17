# JugglucoNG sound collections

Five optional collections, nine cues each: **Contour**, **Porcelain**, **Halo**, **Timber**,
and **Ember**. The original 27 synthesized sounds, names and URIs are preserved unchanged.
Timber and Ember add 18 recorded-acoustic cues. Select a collection in an alert's sound
picker; the alert determines the cue. Global **Apply to all** selects the matching cue
for each standard and custom glucose alert. Existing defaults and external sounds remain
unchanged. Collection names are proper names shared across locales; the surrounding UI
reuses existing translations. The picker is in the phone Compose settings; Wear packages
the assets but its legacy settings do not gain a collection picker.

## Listen

Every reel plays: low, high, urgent low, urgent high, falling, rising, signal, reminder, notice,
with a one-second separator in addition to each cue's release.

- [Contour](contour-preview.wav): warm register, rounded synthesis and dry reflections.
- [Porcelain](porcelain-preview.wav): brighter synthesis, staggered voicings and a reflective tail.
- [Halo](halo-preview.wav): FM, pulse modulation, compact phrases and rhythmic echoes.
- [Timber](timber-preview.wav): soft recorded marimba, vibraphone, woodblock and piano.
- [Ember](ember-preview.wav): lower recorded bodies with separate crisp strikes.

[Original alert-type comparison](alert-identities-preview.wav) demonstrates all nine Halo cues.
The [acoustic study](acoustic-study/README.md) and [low/high A/B](acoustic-study/body-crisp/README.md)
are also retained. Its four softer cues survive byte-for-byte in Timber; the two deeper/crisper
cues survive byte-for-byte in Ember. Both directions are available, not replacements for each other.

## Cue vocabulary

| Cue | Used for | Original synthesized collections | Timber / Ember |
| --- | --- | --- | --- |
| Low | Low glucose, custom low | Hollow falling wobble | Descending marimba strikes |
| High | High/persistent high, custom high | Bright chiming cascade | Vibraphone response |
| Urgent low | Very low | Rough pulses | Two groups of three firmer wooden strikes |
| Urgent high | Very high | Rapid metallic alternation | Two pairs of shorter metal strikes |
| Falling | Forecast low, falling fast | Liquid downward gesture | Short descending piano / marimba figure |
| Rising | Forecast high, rising fast | Airy upward whistle | Compact ascending vibraphone figure |
| Signal | Signal loss, missed reading | Dry ticks and broken radio response | Broken wooden knock pattern |
| Reminder | Amount, sensor expiry | Wooden plucked phrase | Soft piano interval and response |
| Notice | Value available | Airy sparkle | Small marimba/vibraphone flourish |

The acoustic collections retain recorded strikes and resonances; there is no pitch shifting,
added oscillator, synthesized modulation or artificial reverberation. Urgent cues use a tighter
cadence and a 2.5 dB higher active RMS target. All production files are mono 48 kHz, 16-bit PCM
WAV with explicit releases, headroom and zero endpoints. Source recordings, scripts and
preview reels are outside Android resources and do not add to the APK.

## Sources and licensing

The unchanged synthesized collections were composed with modal/additive/FM synthesis at
96 kHz before filtered conversion to 48 kHz, without recordings or third-party loops.
Their loose connection to Juggluco is its short tonal vocabulary, not copied melodies or audio.
Those files and the renderers use the repository's GPL-3.0-or-later license.

**The 18 `alert_timber_*.wav` and `alert_ember_*.wav` files and their preview reels are CC0.**
They arrange recordings from Versilian Studios' [VCSL](https://github.com/sgossner/VCSL), pinned
at `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`. Original source bytes, source URLs, SHA-256 hashes,
and the upstream CC0 license are preserved in [acoustic-study](acoustic-study/README.md).
No recordings are fetched during an Android build. The original stereo samples are downmixed
and resampled by macOS afconvert, then trimmed, gently filtered, arranged and level-balanced.

## Reproduce and verify

Python 3 and NumPy (authored with 2.3.5); acoustic reproduction also requires macOS afconvert:

```sh
python3 tools/alert-sounds/render.py --check
python3 tools/alert-sounds/render_acoustic.py --check
```

Omit `--check` to deliberately regenerate the respective collection group. Each renderer writes
only its own resources. Checks cover exact PCM/reel reproduction, source hashes, peaks, DC offset,
zero endpoints and measurements. Acoustic generation also asserts exact preservation of the six
previously auditioned cues. Exact bytes can depend on NumPy/platform and sample-rate-converter
versions. Measurements are in `measurements.json` (original 27) and `acoustic-measurements.json`
(new 18). Android builds use the committed WAVs and do not require Python or afconvert.

Selections use `android.resource://<applicationId>/raw/<name>` so numeric resource-ID changes
cannot retarget saved choices. `alert_sounds_keep.xml` preserves all five collections during
release shrinking. JVM tests cover selection, global remapping, external URIs, locale-independent
names, all 45 source files, and original-asset hashes. Verify actual APK resource tables too:

```sh
python3 tools/alert-sounds/verify_apk.py --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" path/to/app.apk
```

This verifies all 45 named raw resources and the exact WAV bytes they resolve to, even when
release optimization shortens physical ZIP paths.

## Listening acceptance

Digital checks do not establish everyday preference, physical-speaker audibility, or clinical
alarm recognition. Audition on the target phone/watch with background noise and Bluetooth,
repeated alarms, preview switching/cancellation and saved selections after restart/update.
Physical-device listening acceptance of the new collections is still outstanding. Existing
defaults are not migrated to either new collection.
