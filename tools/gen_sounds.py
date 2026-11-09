#!/usr/bin/env python3
# Fichier ajouté dans le fork Hume Launcher+ : génère les sons de navigation.
"""Generates Hume's navigation sounds (res/raw/nav_*.wav): short, soft, tvOS-like ticks.

Original synthesis (no sampled audio): a damped sine "tock" with a gentle pitch glide and a tiny
filtered-noise transient for the attack. Tweak the parameters below and re-run:
    python3 tools/gen_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "res", "raw")


def tock(dur, f0, f1, decay, gain, click=0.15, body=None, seed=1, attack=0.0008, soft=0.0):
    """dur s; pitch glides f0 -> f1; amplitude decays with time constant `decay` s."""
    rnd = random.Random(seed)
    n = int(dur * RATE)
    out, phase, phase2, lp = [], 0.0, 0.0, 0.0
    for i in range(n):
        t = i / RATE
        f = f1 + (f0 - f1) * math.exp(-t / (dur / 4))
        phase += 2 * math.pi * f / RATE
        env = math.exp(-t / decay)
        s = math.sin(phase) * env
        if body:  # a lower partial for warmth
            bf, bg, bd = body
            phase2 += 2 * math.pi * bf / RATE
            s += bg * math.sin(phase2) * math.exp(-t / bd)
        # attack transient: low-passed noise over the first ~3 ms
        lp += 0.35 * (rnd.uniform(-1, 1) - lp)
        s += click * lp * math.exp(-t / 0.0012)
        # 0.8 ms fade-in and a fade-out over the last 15% to avoid any pop
        s *= min(1.0, t / attack) * min(1.0, (n - i) / (0.15 * n))
        out.append(s * gain)
    # Optional one-pole low-pass: rounds off the top end for a softer, felt-like tap.
    if soft:
        y = 0.0
        for i, v in enumerate(out):
            y += soft * (v - y)
            out[i] = y
    return out


def save(name, samples):
    os.makedirs(OUT, exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = min(1.0, 0.95 / peak)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * scale * 32767)) for s in samples))


# Softer set (v2): lower pitches, no noisy click, a 2-4 ms attack and a low-pass, so moving
# around is a light tap rather than a tick.
# Focus move: a muted, low "tap", very short.
save("nav_move.wav", tock(0.05, 1250, 1050, 0.009, 0.5, click=0.02, body=(620, 0.5, 0.008), seed=3,
                          attack=0.002, soft=0.35))
# Select: a round, warm "pop".
save("nav_select.wav", tock(0.12, 820, 640, 0.024, 0.75, click=0.02, body=(410, 0.6, 0.03), seed=5,
                            attack=0.003, soft=0.3))
# Back: like select, a little lower and softer.
save("nav_back.wav", tock(0.1, 660, 500, 0.02, 0.65, click=0.02, body=(330, 0.55, 0.025), seed=7,
                          attack=0.003, soft=0.3))
print("wrote", sorted(f for f in os.listdir(OUT) if f.startswith("nav_")))
