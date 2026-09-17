#!/usr/bin/env python3
"""Original compositions inspired by mechanical Juggluco alarms; no reused audio.
Render at 96 kHz, low-pass to mono 48 kHz PCM. Python 3 + NumPy.
"""
import argparse
import sys
from pathlib import Path
import json
import numpy as np
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE/'acoustic-study'))
from render_study import encode
from mastering import REFERENCES, master, metrics
from render_acoustic import save_or_check
RATE = 96000
ROOT = HERE.parents[1]

def envelope(n, attack=.025, release=.30):
    e = np.ones(n)
    a, r = min(n,round(attack*RATE)), min(n,round(release*RATE))
    e[:a] *= np.sin(np.linspace(0,np.pi/2,a))**2
    e[-r:] *= np.cos(np.linspace(0,np.pi/2,r))**2
    return e

def horn(duration, lo, hi, cycles, reed=.16):
    t = np.arange(round(duration*RATE))/RATE
    # Motor-driven horn: smooth spin-up/down, warm fundamental and restrained reed.
    sweep = .5-.5*np.cos(2*np.pi*cycles*t/duration)
    phase = 2*np.pi*np.cumsum(lo+(hi-lo)*sweep)/RATE
    x = np.sin(phase)+reed*np.sin(2*phase)+.07*np.sin(3*phase)
    return x*envelope(len(t),.09,.48)

def bell(duration, frequency, seed):
    t = np.arange(round(duration*RATE))/RATE
    rng = np.random.default_rng(seed)
    x = np.zeros(len(t))
    for ratio,gain,decay in ((1,1,1.1),(2.01,.40,.65),(2.76,.18,.40),(4.07,.09,.18)):
        x += gain*np.sin(2*np.pi*frequency*ratio*t)*np.exp(-t/decay)
    x += .035*rng.normal(size=len(t))*np.exp(-t/.006)
    return x*envelope(len(t),.003,.30)

def mechanism(duration, seed, body=190):
    t = np.arange(round(duration*RATE))/RATE
    rng = np.random.default_rng(seed)
    noise = rng.normal(size=len(t))
    noise = np.convolve(noise,np.ones(7)/7,mode='same')
    x = .75*np.sin(2*np.pi*body*t)*np.exp(-t/.13)
    x += .30*np.sin(2*np.pi*body*2.73*t)*np.exp(-t/.045)
    x += .16*noise*np.exp(-t/.012)
    return x*envelope(len(t),.002,.10)

def render(cue):
    length=REFERENCES[cue]['seconds']
    out=np.zeros(REFERENCES[cue]['frames']*2)
    def add(at, x, gain=1):
        start=round(at*RATE)
        if start>=len(out): return
        count=min(len(x),len(out)-start)
        out[start:start+count] += gain*x[:count]
    if cue=='low':
        # Spacious rounded civil-siren calls; a low rotor rather than a beep.
        for i,at in enumerate(np.linspace(0,length-3.6,4)):
            add(at,horn(3.6,240,460,1.25),(.9,1,.92,.82)[i])
    elif cue=='high':
        # Brass fire-engine two-tone, clearly separate from a continuously swept low.
        for at in np.linspace(0,length-1.85,4):
            add(at,horn(.78,392,405,.5,.24),.85)
            add(at+.72,horn(1.13,523,530,.5,.20),.95)
    elif cue=='urgent_low':
        # Faster, lower three-call siren with a short mechanical contact at onset.
        for at in np.linspace(0,length-1.9,3):
            add(at,horn(1.9,165,440,2.6,.25))
            add(at,mechanism(.3,21),.22)
    elif cue=='urgent_high':
        # Alternating struck fire bell, not a pitch-shifted siren.
        for i,at in enumerate(np.arange(0,length-1,.30)):
            add(at,bell(min(1.6,length-at),740 if i%2 else 554,80+i),.8)
    elif cue=='falling':
        # Flywheel winding down: broad decreasing pitch and decelerating contacts.
        for at in (0,length/2):
            dur=length/2
            t=np.arange(round(dur*RATE))/RATE
            f=160+420*np.exp(-t/1.05)
            ph=2*np.pi*np.cumsum(f)/RATE
            add(at,(np.sin(ph)+.12*np.sin(2*ph))*envelope(len(t),.12,.75),.72)
            for j,offset in enumerate((.1,.36,.74,1.28,2.0)):
                add(at+offset,mechanism(.24,50+j,220),.18)
    elif cue=='rising':
        # Whistling kettle/steam call: rising air with a stable warm lower resonance.
        for at in np.linspace(0,length-2.05,3):
            t=np.arange(round(2.05*RATE))/RATE
            f=420+460*(1-np.exp(-t/.5))
            ph=2*np.pi*np.cumsum(f)/RATE
            x=np.sin(ph)*(1+.035*np.sin(2*np.pi*5*t))+.22*np.sin(ph/2)
            add(at,x*envelope(len(t),.28,.58),.8)
    elif cue=='signal':
        # Relay's unanswered double-knock, then a lower latch.
        for at in (0,1.35,2.65):
            add(at,mechanism(.45,12,310),.8)
            add(at+.20,mechanism(.42,13,310),.65)
            add(at+.69,mechanism(.72,14,145),1)
    elif cue=='reminder':
        # Desk/service bell with measured space and natural resonant tails.
        for i,at in enumerate(np.linspace(0,length-1.7,3)):
            add(at,bell(1.7,440,30+i),.8)
            add(at+.14,mechanism(.2,90+i),.13)
    else:
        # Compact music-box acknowledgement, repeated softly rather than a ghost gag.
        for at in (0,3.1,6.2):
            for j,f in enumerate((523.25,659.25,783.99)):
                add(at+j*.23,bell(1.9,f,100+j),.7-j*.10)
    # Small early reflections anchor the mechanical instruments in a room, not a wash.
    dry=out.copy()
    for delay,gain in ((.031,.085),(.057,.045)):
        n=round(delay*RATE); out[n:]+=dry[:-n]*gain
    out *= envelope(len(out),.008,.60)
    n=np.arange(-96,97)
    kernel=(2*19000/RATE)*np.sinc(2*19000*n/RATE)*np.kaiser(len(n),9)
    kernel/=kernel.sum()
    return master(np.convolve(out,kernel,mode='same')[::2],cue)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    report={}; reel=[]
    for cue in ('low','high','urgent_low','urgent_high','falling','rising','signal','reminder','notice'):
        pcm=render(cue); data=encode(pcm)
        name=f'alert_juggluco_{cue}.wav'
        save_or_check(ROOT/'Common/src/main/res/raw'/name,data,args.check)
        report[name]=metrics(pcm,data)
        reel.extend((pcm,np.zeros(48000,dtype='<i2')))
    save_or_check(HERE/'juggluco-preview.wav',encode(np.concatenate(reel)),args.check)
    save_or_check(HERE/'juggluco-measurements.json',(json.dumps(report,indent=2)+'\n').encode(),args.check)
    print(('Verified' if args.check else 'Rendered')+' 9 modern Juggluco cues with original durations')
if __name__=='__main__': main()
