# JugglucoNG sound collections

Six collections, nine cues each: **Contour**, **Porcelain**, **Halo**, **Timber**,
**Ember**, and **Juggluco**. The original 27 synthesized sounds, names and URIs are preserved unchanged.
Timber and Ember add 18 recorded-acoustic cues; Juggluco remasters the nine original
MP3/OGG recordings to modern-quality WAVs with the same melodies and durations. Select a collection in an alert's sound
picker; the alert determines the cue. Juggluco is listed first, directly below System Default;
tapping a row confirms it immediately (Cancel dismisses without changing anything). Global **Apply to all** selects the matching cue
for each standard and custom glucose alert. **Ember is the app default** for unset sounds, including Wear playback. Explicit collection,
external, system-default and saved native sound choices are retained. Choosing App Default
in the phone picker previews Ember and clears the previous per-alert choice. Collection names are proper names shared across locales; the surrounding UI
reuses existing translations. The picker is in the phone Compose settings; Wear packages
the assets but its legacy settings do not gain a collection picker.

## Listen

Every reel plays: low, high, urgent low, urgent high, falling, rising, signal, reminder, notice,
with a one-second separator in addition to each cue's release.

- [Contour](contour-preview.wav): warm register, rounded synthesis and dry reflections.
- [Porcelain](porcelain-preview.wav): brighter synthesis, staggered voicings and a reflective tail.
- [Halo](halo-preview.wav): FM, pulse modulation, compact phrases and rhythmic echoes.
- [Timber](timber-preview.wav): soft recorded marimba, vibraphone, woodblock and piano.
- [Ember](ember-preview.wav): lower recorded bodies with separate crisp strikes; app default.
- [Juggluco](juggluco-preview.wav): the familiar siren, classic, ghost, nudge, elves, verylow, veryhigh, lowsoon and highsoon melodies, remastered.

[Original alert-type comparison](alert-identities-preview.wav) demonstrates all nine Halo cues.
The [acoustic study](acoustic-study/README.md) and [low/high A/B](acoustic-study/body-crisp/README.md)
are retained byte-for-byte as short studies. Their phrases now develop into longer
Timber and Ember arrangements; both remain selectable alongside all three previous collections.

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
cadence and a higher active RMS target. All production files are mono 48 kHz, 16-bit PCM
WAV with explicit releases, headroom and zero endpoints. Source recordings, scripts and
preview reels are outside Android resources and do not add to the APK.

## Duration and mastering

Timber, Ember and the Juggluco remasters match the decoded 48 kHz frame count of each
original cue. [original-reference.json](original-reference.json) records source hashes,
durations and measured electrical levels (mono afconvert decode). Mapping: low=siren,
high=classic, notice=ghost, reminder=nudge, signal=elves, urgent low=verylow,
urgent high=veryhigh, falling=lowsoon, rising=highsoon. Durations range from 4.125 to 15.768 s.
Acoustic motifs return with breathing space and varied accents, then a longer final decay;
the source recordings are never time-stretched. The three earlier synthesized sets keep
all their original bytes, durations and levels.

Acoustic masters sit between the quiet auditions and the rejected loud version:
-17.5 dBFS active RMS for ordinary cues and -15.25 dBFS for urgent cues, with a -3 dBFS
sample-peak ceiling. A single linear gain is
applied; if the peak ceiling is reached first, the lower RMS is retained. There is no
waveshaping, compression or saturation. This preserves recorded transients instead of
forcing them into a loudness target. Active RMS excludes samples below 1% of peak and
is not a claim of matched perceived loudness. The longer arrangements and releases remain.

The Juggluco remasters decode those same MP3/OGG sources to the exact reference
frame counts, then apply one linear gain with the same targets (ordinary -17.5 dBFS,
urgent -15.25 dBFS, -3 dBFS peak ceiling), DC removal, 5 ms fades and zero endpoints.
Interior samples match the originals up to that single gain (≤0.001 full scale):
no compression, saturation, pitch or time changes. This fixes the hot masters
(nudge peaked at -1.9 dBFS), the click-prone onsets (elves, ghost), the DC offset
(highsoon) and the level spread, while keeping every cue recognizable. The legacy
MP3/OGG files stay packaged so previously saved Juggluco choices keep playing;
those saved URIs still read as Juggluco and remap to the matching remaster on
**Apply to all**. The existing user-configured alarm duration still controls playback and may stop a cue
before its recorded ending; this change does not extend or override alarm timers.

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

The nine `alert_juggluco_*.wav` remasters derive from the repository's own legacy
originals, so they use the repository's GPL-3.0-or-later license like the synthesized
collections.

## Reproduce and verify

Python 3 and NumPy (authored with 2.3.5); acoustic and Juggluco reproduction also require macOS afconvert:

```sh
python3 tools/alert-sounds/render.py --check
python3 tools/alert-sounds/render_acoustic.py --check
python3 tools/alert-sounds/render_juggluco.py --check
```

Omit `--check` to deliberately regenerate the respective collection group. Each renderer writes
only its own resources. Checks cover exact PCM/reel reproduction, source hashes, peaks, DC offset,
zero endpoints and measurements. The short studies remain separate, unchanged artifacts. Exact bytes can depend on NumPy/platform and sample-rate-converter
versions. Measurements are in `measurements.json` (original 27), `acoustic-measurements.json`
(new acoustic 18) and `juggluco-measurements.json` (remastered 9). Android builds use the committed WAVs and do not require Python or afconvert.

Selections use `android.resource://<applicationId>/raw/<name>` so numeric resource-ID changes
cannot retarget saved choices. `alert_sounds_keep.xml` preserves all six collections and legacy raw sounds during
release shrinking. JVM tests cover selection, global remapping, external URIs, locale-independent
names, all 54 WAV files, legacy Juggluco URI compatibility, and original-asset hashes. Verify actual APK resource tables too:

```sh
python3 tools/alert-sounds/verify_apk.py --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" path/to/app.apk
```

This verifies all 54 named WAV resources, nine legacy originals, and the exact audio bytes they resolve to, even when
release optimization shortens physical ZIP paths.

## Listening acceptance

Digital checks do not establish everyday preference, physical-speaker audibility, or clinical
alarm recognition. Audition on the target phone/watch with background noise and Bluetooth,
repeated alarms, preview switching/cancellation and saved selections after restart/update.
Physical-device listening acceptance of the new collections is still outstanding. Unset app-default sounds now resolve to Ember; explicit choices are not migrated.
