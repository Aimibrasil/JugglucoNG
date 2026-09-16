# Recorded-acoustic listening study

An audition for the proposed acoustic direction, separate from the currently packaged
synthesized collections. These four cues are not Android resources and are not in the APK.
The point of this study is to settle the material and character through listening before
replacing the production set.

[Listen to the audition](acoustic-audition.wav), in this order:

1. [Low](low.wav): two soft marimba strikes with a descending response.
2. [High](high.wav): two soft-mallet vibraphone strikes with natural metal resonance.
3. [Signal loss](signal.wav): a broken pattern of quiet wooden knocks.
4. [Reminder](reminder.wav): a softly played piano interval and a short response.

All sounds use real instrument recordings. There are no synthesized oscillators, pitch
sweeps, pitch shifting, synthetic reverberation, or electronic modulation. Edits are
high-quality conversion to mono 48 kHz, onset trimming, gentle low-pass filtering,
level balancing, arrangement, and smooth shortening of the recorded decays. The piano
was recorded normally at soft velocity; it is not a recording of a felt-modified piano.

## Source and rights

Samples: Versilian Studios' [Versilian Community Sample Library](https://github.com/sgossner/VCSL),
revision `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`, published under **CC0 1.0**.
See the upstream [README](https://github.com/sgossner/VCSL/blob/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e/README.md),
the preserved [license](CC0.txt), and [source manifest](sources.json) for exact URLs and hashes.
Original source recordings are preserved unchanged in `sources/`; their Git blob hashes
were checked against the upstream tree when downloaded. The arrangements and audition
WAVs in this folder are also dedicated under CC0. The renderer follows the repository's
GPL-3.0-or-later license. This source exception applies only to this study folder.

## Reproduce

Requires macOS `afconvert`, Python 3 and NumPy (authored with NumPy 2.3.5):

```sh
python3 tools/alert-sounds/acoustic-study/render_study.py
python3 tools/alert-sounds/acoustic-study/render_study.py --check
```

The check validates source hashes, reproduces all four cues and the reel, checks digital
peaks, DC offset and zero endpoints, and compares `measurements.json`. Exact bytes can
depend on the macOS sample-rate converter and NumPy versions. No physical-device
recognition or listening acceptance is claimed. These are character studies, not
validated urgent-alarm designs.
