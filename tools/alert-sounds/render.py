#!/usr/bin/env python3
"""Render original JugglucoNG earcons with Python 3 and NumPy, without samples.
--check verifies committed PCM against the deterministic scores.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RATE = 48000
SYNTH_RATE = RATE * 2
CUES = ('low', 'high', 'urgent_low', 'urgent_high', 'falling', 'rising', 'signal', 'reminder', 'notice')
# Each family has its own compositions, not just a different patch on one melody.
# Event: onset seconds, MIDI pitch, articulation seconds, velocity.
SCORES = {
    'contour': {
        'low': [(0, 79, .24, 1), (.24, 74, .20, .85), (.60, 67, .40, 1), (.77, 67, .16, .40)],
        'high': [(0, 67, .22, 1), (.30, 74, .20, .8), (.48, 79, .40, 1)],
        'urgent_low': [(b+t, p, .18, v) for b in (0, .82) for t,p,v in ((0,81,1),(.18,74,.9),(.37,67,1))],
        'urgent_high': [(b+t, p, .19, v) for b in (0,.86) for t,p,v in ((0,69,.9),(.22,81,1))],
        'falling': [(0,79,.20,.8),(.19,76,.17,.65),(.44,71,.33,1)],
        'rising': [(0,71,.19,.8),(.23,76,.17,.65),(.42,79,.34,1)],
        'signal': [(0,74,.12,.9),(.15,74,.12,.65),(.61,67,.32,1)],
        'reminder': [(0,67,.20,1),(.30,74,.16,.65),(.46,71,.26,.85),(.74,79,.30,.8)],
        'notice': [(0,74,.24,.85),(.075,79,.30,.7)],
    },
    'porcelain': {
        'low': [(0,83,.31,1),(.07,78,.24,.38),(.43,78,.28,.8),(.91,71,.38,1)],
        'high': [(0,71,.30,.85),(.38,78,.29,.85),(.79,83,.40,1),(.85,90,.23,.27)],
        'urgent_low': [(b+t,p,.22,v) for b in (0,.92) for t,p,v in ((0,83,1),(.20,78,.85),(.42,71,1))],
        'urgent_high': [(b+t,p,.26,v) for b in (0,.98) for t,p,v in ((0,76,.85),(.31,88,1))],
        'falling': [(0,86,.27,.9),(.30,81,.32,1),(.36,74,.20,.28)],
        'rising': [(0,74,.26,.8),(.34,81,.32,1),(.41,86,.22,.35)],
        'signal': [(0,81,.14,.85),(.20,81,.14,.85),(.89,70,.35,1)],
        'reminder': [(0,74,.29,.8),(.11,81,.30,.55),(.52,78,.35,.95),(.64,86,.28,.5)],
        'notice': [(0,78,.29,.9),(.09,85,.33,.6),(.17,90,.22,.25)],
    },
    'halo': {
        'low': [(0,81,.21,.9),(.28,76,.19,.7),(.64,69,.34,1)],
        'high': [(0,69,.18,.85),(.33,76,.17,.7),(.53,81,.32,1)],
        'urgent_low': [(b+t,p,.14,v) for b in (0,.76) for t,p,v in ((0,81,1),(.17,76,.8),(.34,69,1))],
        'urgent_high': [(b+t,p,.19,v) for b in (0,.84) for t,p,v in ((0,72,.85),(.25,84,1))],
        'falling': [(0,81,.24,.9),(.34,72,.29,1)],
        'rising': [(0,72,.24,.9),(.35,81,.29,1)],
        'signal': [(0,78,.095,.9),(.16,78,.095,.8),(.64,66,.26,1)],
        'reminder': [(0,69,.16,.9),(.28,76,.12,.6),(.46,73,.18,.8),(.78,81,.25,1)],
        'notice': [(0,76,.16,.85),(.15,83,.27,1)],
    },
}


def voice(style, midi, duration, cue, seed):
    t = np.arange(round(SYNTH_RATE * (duration + .68))) / SYNTH_RATE
    f = 440 * 2 ** ((midi - 69) / 12)
    if style == 'contour':
        # Wooden thumb-piano/marimba hybrid: lower body and a dry, pitched knock.
        f *= .75
        tone = np.zeros_like(t)
        for ratio, level, decay in ((1,1,.31),(2,.32,.12),(3.96,.24,.052),(6.15,.07,.023)):
            tone += level * np.sin(2*np.pi*f*ratio*t) * np.exp(-t/(decay+duration*.28))
        rng = np.random.default_rng(seed)
        noise = np.convolve(rng.normal(size=len(t)), np.ones(13)/13, mode='same')
        tone += .15 * noise * np.exp(-t/.009)
        tone += .20 * np.sin(2*np.pi*f*.5*t) * np.exp(-t/.12)
        tone *= (1-np.exp(-t/.0025))
        tone = np.tanh(1.25*tone)/1.25
    elif style == 'porcelain':
        # Struck glass modes: audible inharmonic overtones, rapidly darkening attack,
        # and a quiet detuned resonant pair rather than a plain sine-wave chime.
        tone = np.zeros_like(t)
        for ratio, level, decay in ((1,1,.38),(2.71,.42,.12),(4.08,.19,.055),(5.43,.075,.025)):
            tone += level*np.sin(2*np.pi*f*ratio*t)*np.exp(-t/(decay+duration*.5))
        tone += .12*np.sin(2*np.pi*f*1.004*t)*np.exp(-t/.36)
        tone *= (1-np.exp(-t/.0018))
    else:
        # FM pulse with an opening/closing harmonic envelope and a short pitch pickup.
        # Direction remains audible even before the second note of a trend cue.
        direction = -1 if cue in ('low','urgent_low','falling') else 1
        semitones = direction * (-1.5*np.exp(-t/.035))
        freq = f * 2**(semitones/12)
        phase = 2*np.pi*np.cumsum(freq)/SYNTH_RATE
        index = 1.7*np.exp(-t/.062)+.16
        tone = np.sin(phase + index*np.sin(2*phase))
        tone += .19*np.sin(phase*.5) + .12*np.sin(phase*1.005)
        attack = 1-np.exp(-t/.004)
        gate = np.exp(-np.maximum(0,t-duration)/.035)
        pulse = .84+.16*np.cos(2*np.pi*7*t)
        tone *= attack*gate*pulse
    # Every voice terminates smoothly before the next loop. No stereo phase tricks.
    tone *= np.minimum(1, np.maximum(0, (duration+.68-t)/.16))**2
    return tone


def render(style, cue):
    score = SCORES[style][cue]
    out = np.zeros(round(SYNTH_RATE*(max(t+d for t,_,d,_ in score)+1.02)))
    reflections = {
        'contour': ((0,1),(.063,.065),(.107,.025)),
        'porcelain': ((0,1),(.053,.13),(.097,.085),(.151,.045)),
        'halo': ((0,1),(.105,.15),(.21,.045)),
    }
    for i, (onset,midi,duration,strength) in enumerate(score):
        tone = voice(style,midi,duration,cue,1000*CUES.index(cue)+i)
        for delay,gain in reflections[style]:
            at = round((onset+delay)*SYNTH_RATE)
            n = min(len(tone),len(out)-at)
            out[at:at+n] += strength*gain*tone[:n]
    # 2x oversampling with a windowed-sinc low-pass before decimation, including FM
    # and saturation harmonics. FIR group delay is removed by 'same' convolution.
    x = np.arange(-96,97)
    kernel = .44*np.sinc(.44*x)*np.kaiser(len(x),9)
    kernel /= kernel.sum()
    out = np.convolve(out,kernel,mode='same')[::2]
    out -= np.mean(out)
    fade = round(.004*RATE)
    out[:fade] *= np.linspace(0,1,fade)**2
    out[-round(.18*RATE):] *= np.linspace(1,0,round(.18*RATE))**2
    target_rms = 10**((-16 if cue.startswith('urgent') else -18.5)/20)
    active = out[np.abs(out)>.012*np.max(np.abs(out))]
    out *= min(target_rms/np.sqrt(np.mean(active**2)),10**(-3/20)/np.max(np.abs(out)))
    return np.rint(out*32767).astype('<i2')


def wav(samples):
    stream = io.BytesIO()
    with wave.open(stream,'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(samples.tobytes())
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args = parser.parse_args()
    stats = {}
    for style in SCORES:
        reel = []
        for cue in CUES:
            name = f'alert_{style}_{cue}.wav'
            samples = render(style,cue)
            data = wav(samples)
            dest = ROOT/'Common/src/main/res/raw'/name
            if args.check:
                assert dest.read_bytes()==data, f'{name}: differs from score'
            else:
                dest.write_bytes(data)
            normalized = samples.astype(float)/32768
            assert np.max(np.abs(normalized))<.709, name
            assert samples[0]==samples[-1]==0, name
            assert abs(np.mean(normalized))<.001, name
            stats[name] = dict(seconds=round(len(samples)/RATE,3),
                               peak_dbfs=round(20*np.log10(np.max(np.abs(normalized))),2),
                               rms_dbfs=round(20*np.log10(np.sqrt(np.mean(normalized**2))),2),
                               sha256=hashlib.sha256(data).hexdigest())
            reel.extend((samples,np.zeros(RATE,dtype='<i2')))
        dest = ROOT/'tools/alert-sounds'/f'{style}-preview.wav'
        data = wav(np.concatenate(reel))
        if args.check:
            assert dest.read_bytes()==data, str(dest)
        else:
            dest.write_bytes(data)
    assert len({s['sha256'] for s in stats.values()})==27
    report = json.dumps(stats,indent=2)+'\n'
    dest = ROOT/'tools/alert-sounds/measurements.json'
    if args.check:
        assert dest.read_text()==report
    else:
        dest.write_text(report)
    print(f'{"Verified" if args.check else "Rendered"} {len(stats)} mono 48 kHz PCM sounds and 3 preview reels')


if __name__=='__main__':
    main()
