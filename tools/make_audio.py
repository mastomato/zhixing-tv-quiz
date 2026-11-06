"""Generate original, royalty-free PCM WAV cues for the TV quiz."""

from math import cos, exp, pi, sin
from pathlib import Path
import struct
import wave

RATE = 22050
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
OUT.mkdir(parents=True, exist_ok=True)


def tone(freq, t, harmonics=0.13):
    return sin(2 * pi * freq * t) + harmonics * sin(4 * pi * freq * t)


def write(name, seconds, sample):
    frames = bytearray()
    count = int(seconds * RATE)
    for i in range(count):
        t = i / RATE
        value = max(-0.92, min(0.92, sample(t, seconds)))
        frames.extend(struct.pack("<h", int(value * 32767)))
    with wave.open(str(OUT / name), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(frames)


chords = [
    (130.81, 261.63, 329.63, 392.00),  # C
    (110.00, 220.00, 261.63, 329.63),  # Am
    (87.31, 174.61, 220.00, 261.63),   # F
    (98.00, 196.00, 246.94, 392.00),   # G
]


def background(t, duration):
    bar = min(3, int(t // 4))
    bass, a, b, c = chords[bar]
    pad = sum(tone(f, t, .07) for f in (a, b, c)) / 3
    step = int((t % 4) * 2)
    note = (a, b, c, b, a, c, b, c)[step] * 2
    local = t % .5
    arpeggio = tone(note, t, .08) * exp(-7 * local)
    fade = min(1, t / .22, (duration - t) / .22)
    return fade * (.11 * pad + .075 * tone(bass, t, .02) + .12 * arpeggio)


def cue(notes, t, duration, gain):
    result = 0.0
    for start, freq, length in notes:
        local = t - start
        if 0 <= local <= length:
            envelope = min(1, local / .015) * exp(-3.7 * local / length)
            result += tone(freq, local, .18) * envelope
    return gain * result * min(1, (duration - t) / .04)


write("bgm.wav", 16, background)
write("correct.wav", .65, lambda t, d: cue([
    (0, 523.25, .30), (.14, 659.25, .34), (.29, 783.99, .34)], t, d, .28))
write("wrong.wav", .72, lambda t, d: cue([
    (0, 392.00, .32), (.23, 311.13, .35)], t, d, .28))
write("complete.wav", 2.1, lambda t, d: cue([
    (0, 523.25, .48), (.25, 659.25, .48), (.50, 783.99, .55),
    (.83, 1046.50, 1.15)], t, d, .25))

if __name__ == "__main__":
    for audio in sorted(OUT.glob("*.wav")):
        print(audio.name, audio.stat().st_size, "bytes")
