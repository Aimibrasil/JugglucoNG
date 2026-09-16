# JugglucoNG sound collections

27 original synthesized cues in three optional collections. Select **Contour**, **Porcelain**,
or **Halo** in an alert's sound picker. The alert determines the cue. The global **Apply to all**
action selects that collection's corresponding cue for each standard and custom glucose alert.
Existing app defaults and saved external sounds remain unchanged. Collection names are proper
names and remain the same in every locale; the surrounding picker uses existing translations.
Selection is exposed in the phone Compose settings. The assets are also packaged for Wear;
this change does not add a collection picker to the legacy Wear settings.

## Listen

Each reel plays: low, high, urgent low, urgent high, falling, rising, signal, reminder, notice.
There is a one-second gap between cues, in addition to each cue's release.

Start with the [alert-type comparison](alert-identities-preview.wav): all nine cue types
from Halo in the order above. This isolates the differences between alert categories.

Each category has its own synthesis instrument, envelope, and rhythmic silhouette. The
collections are secondary material treatments of those identities:

- [Contour](contour-preview.wav): a warmer register, rounded saturation, and dry reflections.
- [Porcelain](porcelain-preview.wav): a brighter register, staggered voicings, and a reflective tail.
- [Halo](halo-preview.wav): pulse modulation, compact phrases, and rhythmic echoes.

Synthesis runs at 96 kHz and is low-pass filtered before conversion to 48 kHz to control
high-frequency artifacts from FM and saturation.

These are original modal/additive/FM synthesis compositions, with no recordings, sampled instruments,
third-party loops, or generated speech. The loose connection to Juggluco is its short tonal
alert vocabulary, not reuse of its melodies or recordings. Renderer and audio are distributed
under the repository's GPL-3.0-or-later license.

## Cue vocabulary

| Cue | Used for | Audible structure |
| --- | --- | --- |
| Low | Low glucose, custom low | Two hollow falling wobbles with moving formants |
| High | High/persistent high, custom high | Bright inharmonic chiming cascade |
| Urgent low | Very low | Rough, weightier pulses in two groups of three |
| Urgent high | Very high | Rapid metallic alternation in two groups of two |
| Falling | Forecast low, falling fast | Liquid downward spring/zipper gesture |
| Rising | Forecast high, rising fast | Airy accelerating upward whistle |
| Signal | Signal loss, missed reading | Dry ticks, a pause, then a broken radio-like response |
| Reminder | Amount, sensor expiry | Warm wooden plucked phrase |
| Notice | Value available | Short airy sparkle |

Urgency is conveyed by rhythm and a 2.5 dB increase in the synthesis RMS target, rather than
an abrasive siren. Cues are 1.40–2.58 seconds long. They have explicit attacks/releases, short
mono reflections, and a quiet tail for repeated playback. Mono avoids stereo cancellation
and is appropriate to a phone speaker. Production files are lossless 48 kHz, 16-bit PCM WAV.
All 27 resources total about 5.1 MB before APK compression. Preview reels are outside Android
resources and are not packaged into the app.

## Reproduce and verify

Use Python 3 with NumPy (authored with NumPy 2.3.5):

```sh
python3 tools/alert-sounds/render.py
python3 tools/alert-sounds/render.py --check
```

`--check` compares the committed PCM and reels with the deterministic score, checks peaks,
DC offset and zero endpoints, and verifies the measured durations/RMS/peak levels and hashes
in `measurements.json`. Exact PCM reproduction can depend on the NumPy/platform math runtime.
Keep generated files together when deliberately revising a composition.

Android selections use `android.resource://<applicationId>/raw/<name>` so an app update cannot
retarget a saved selection through a reassigned numeric resource ID. `alert_sounds_keep.xml`
preserves those dynamically resolved resources during release shrinking. JVM tests cover
cue assignment, global family application, external-URI preservation, locale-independent
resource names, and the presence of all referenced WAV files. Inspect the release APK as well:
all 27 named raw resources and their original PCM bytes must survive shrinking. Release
optimization can shorten the physical ZIP paths; verify through the resource table:

```sh
python3 tools/alert-sounds/verify_apk.py --aapt2 "$ANDROID_HOME/build-tools/37.0.0/aapt2" path/to/app.apk
```

## Listening acceptance

Automated checks do not establish preference, real-world audibility, or clinical alarm
recognition. Before choosing these as defaults, audition on the target phone/watch speaker
at everyday volume, with background noise and Bluetooth routing, including repeated alarms,
preview switching, cancellation, and a saved selection after app restart/update. Compare
low versus high and regular versus urgent cues without looking at the screen. Audition feedback led to separate sonic identities for the alert categories, with collection
color secondary to recognition of low, high, signal, and other alert types. Physical-device
and everyday listening acceptance of the revised audio is still outstanding.
