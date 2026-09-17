#!/usr/bin/env python3
"""Render additive Timber and Ember collections from verified CC0 recordings.
Requires Python, NumPy and macOS afconvert. Existing synthesized files are never written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
STUDY = HERE / 'acoustic-study'
sys.path.insert(0, str(STUDY))
from render_study import RATE, read_sample, encode, SCORES as SOFT
from render_body_crisp import SCORES as BODY_CRISP

CUES = ('low', 'high', 'urgent_low', 'urgent_high', 'falling', 'rising', 'signal', 'reminder', 'notice')
# Preserve the already-auditioned four Timber and two Ember cues exactly, then
# complete their alert vocabulary. Entries: onset, recording, level, decay seconds.
SCORES = {
    'timber': {
        **SOFT,
        'urgent_low': [(b+t, name, gain, .32) for b in (0, .90)
                       for t, name, gain in ((0,'marimba_b2_med',1),(.20,'marimba_c2_med',.9),(.40,'marimba_c2_med',1))],
        'urgent_high': [(b+t, name, gain, .42) for b in (0, .94)
                        for t, name, gain in ((0,'vibes_f4',.9),(.25,'vibes_a4',1))],
        'falling': [(0,'piano_g3',.8,.56),(.24,'piano_c3',1,.80)],
        'rising': [(0,'vibes_f2',.75,.50),(.18,'vibes_a2',.9,.55),(.40,'vibes_f4',.65,.70)],
        'notice': [(0,'marimba_c4',.7,.68),(.07,'vibes_f4',.35,.85)],
    },
    'ember': {
        **BODY_CRISP,
        'urgent_low': [(b+t,name,gain,d) for b in (0,.83)
                       for t,name,gain,d in ((0,'marimba_b2_med',1,.29),(.02,'wood_crisp',.19,.09),
                                             (.19,'marimba_c2_med',.95,.29),(.38,'marimba_c2_med',1,.32),(.40,'wood_crisp',.2,.09))],
        'urgent_high': [(b+t,name,gain,d) for b in (0,.88)
                        for t,name,gain,d in ((0,'vibes_f2',.9,.35),(.008,'vibes_attack',.32,.12),
                                              (.24,'vibes_a2',1,.38),(.248,'vibes_attack',.28,.12))],
        'falling': [(0,'marimba_b2_med',1,.46),(.016,'wood_crisp',.13,.08),(.25,'marimba_c2_med',1,.65)],
        'rising': [(0,'vibes_f2',1,.48),(.22,'vibes_a2',1,.55),(.43,'vibes_attack',.24,.20)],
        'signal': [(0,'wood_crisp',.7,.13),(.20,'wood_2',.65,.13),
                   (.71,'wood_crisp',.9,.16),(.735,'marimba_c2_med',.4,.38)],
        'reminder': [(0,'piano_c3',.9,.94),(.025,'wood_1',.08,.08),(.09,'piano_g3',.7,.98),
                     (.54,'piano_g3',.85,1.08),(.55,'wood_2',.07,.08)],
        'notice': [(0,'marimba_b2_med',.8,.52),(.018,'wood_crisp',.08,.07),(.11,'vibes_a2',.65,.74)],
    },
}


def render(style, cue, samples):
    score = SCORES[style][cue]
    quiet_tail = .25 if style == 'timber' else .20
    out = np.zeros(round((max(t+d for t,_,_,d in score)+quiet_tail)*RATE))
    for onset, name, strength, duration in score:
        x = samples[name][:round(duration*RATE)].copy()
        if style == 'timber':
            release = min(round(.45*RATE),len(x))
        else:
            release = min(round(min(.28,duration*.5)*RATE),len(x))
        x[-release:] *= np.cos(np.linspace(0,np.pi/2,release))**2
        at = round(onset*RATE)
        out[at:at+len(x)] += strength*x
    active = out[np.abs(out)>.01*np.max(np.abs(out))]
    target = -17.5 if cue.startswith('urgent') else -20
    out *= min(10**(target/20)/np.sqrt(np.mean(active**2)),10**(-3/20)/np.max(np.abs(out)))
    out[:96] *= np.linspace(0,1,96)**2
    out[-96:] *= np.linspace(1,0,96)**2
    assert np.max(np.abs(out)) < .709 and abs(np.mean(out)) < .001
    pcm = np.rint(out*32767).astype('<i2')
    assert pcm[0] == pcm[-1] == 0
    return pcm


def save_or_check(path, data, check):
    if check:
        assert path.read_bytes() == data, f'{path}: output differs'
    else:
        path.write_bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    manifest = json.loads((STUDY/'sources.json').read_text())
    for name, info in manifest.items():
        source = STUDY/'sources'/name if name.endswith('.wav') else STUDY/name
        assert hashlib.sha256(source.read_bytes()).hexdigest() == info['sha256'], name
    with tempfile.TemporaryDirectory() as temp:
        samples = {p.stem:read_sample(p,Path(temp)) for p in (STUDY/'sources').glob('*.wav')}
    metrics = {}
    for style in SCORES:
        reel = []
        assert set(SCORES[style]) == set(CUES)
        for cue in CUES:
            pcm = render(style,cue,samples)
            data = encode(pcm)
            # Keep both branches of the earlier A/B as finished selectable options.
            if style == 'timber' and cue in SOFT:
                assert data == (STUDY/f'{cue}.wav').read_bytes(), (style,cue)
            if style == 'ember' and cue in BODY_CRISP:
                assert data == (STUDY/'body-crisp'/f'{cue}.wav').read_bytes(), (style,cue)
            name = f'alert_{style}_{cue}.wav'
            save_or_check(ROOT/'Common/src/main/res/raw'/name,data,args.check)
            normalized = pcm.astype(float)/32768
            metrics[name] = {
                'seconds':round(len(pcm)/RATE,3),
                'peak_dbfs':round(20*np.log10(np.max(np.abs(normalized))),2),
                'rms_dbfs':round(20*np.log10(np.sqrt(np.mean(normalized**2))),2),
                'sha256':hashlib.sha256(data).hexdigest(),
            }
            reel.extend((pcm,np.zeros(RATE,dtype='<i2')))
        save_or_check(HERE/f'{style}-preview.wav',encode(np.concatenate(reel)),args.check)
    assert len({value['sha256'] for value in metrics.values()}) == 18
    save_or_check(HERE/'acoustic-measurements.json',(json.dumps(metrics,indent=2)+'\n').encode(),args.check)
    print(('Verified' if args.check else 'Rendered')+' 18 acoustic cues and 2 reels; all six auditioned cues preserved exactly')


if __name__ == '__main__':
    main()
