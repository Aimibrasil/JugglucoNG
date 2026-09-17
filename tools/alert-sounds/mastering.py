"""Shared duration references and conservative soft-knee peak control (48 kHz)."""
import json
from pathlib import Path
import numpy as np
RATE = 48000
REFERENCES = json.loads((Path(__file__).parent/'original-reference.json').read_text())

def master(out, cue):
    assert len(out) == REFERENCES[cue]['frames']
    out = out - np.mean(out)
    # Bring quiet references up to a usable family floor; retain louder original
    # category targets. Electrical active RMS is not perceptual loudness (LUFS).
    target = max(REFERENCES[cue]['active_rms_dbfs'], -15.0)
    if cue.startswith('urgent'):
        target = max(target, -13.0)
    amplitude = 10**(target/20)
    for _ in range(12):
        active = out[np.abs(out) > .01*np.max(np.abs(out))]
        out *= amplitude / np.sqrt(np.mean(active**2))
        # Unity slope below the knee, smooth bounded transients above it.
        magnitude = np.abs(out)
        excess = np.maximum(0, magnitude-.65)
        out = np.sign(out)*np.where(magnitude <= .65, magnitude, .65+.24*np.tanh(excess/.24))
    out[:240] *= np.sin(np.linspace(0,np.pi/2,240))**2
    out[-240:] *= np.cos(np.linspace(0,np.pi/2,240))**2
    out[0] = out[-1] = 0
    assert np.max(np.abs(out)) < .90 and abs(np.mean(out)) < .002
    return np.rint(out*32767).astype('<i2')

def metrics(pcm, data):
    import hashlib
    x = pcm.astype(float)/32768
    active = x[np.abs(x) > .01*np.max(np.abs(x))]
    return dict(frames=len(x), seconds=len(x)/RATE,
        peak_dbfs=round(float(20*np.log10(np.max(np.abs(x)))),2),
        rms_dbfs=round(float(20*np.log10(np.sqrt(np.mean(x*x)))),2),
        active_rms_dbfs=round(float(20*np.log10(np.sqrt(np.mean(active*active)))),2),
        sha256=hashlib.sha256(data).hexdigest())
