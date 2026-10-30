"""Generate a short looping mechanical drone hum as OGG Vorbis."""
from __future__ import annotations

import math
import struct
import subprocess
import wave
from pathlib import Path

import imageio_ffmpeg

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/wrights/sounds/entity/drone_loop.ogg"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sr = 22050
    dur = 1.5
    n = int(sr * dur)
    samples: list[float] = []
    edge = int(0.02 * sr)
    for i in range(n):
        t = i / sr
        v = (
            0.28 * math.sin(2 * math.pi * 85 * t)
            + 0.18 * math.sin(2 * math.pi * 170 * t)
            + 0.12 * math.sin(2 * math.pi * 255 * t)
            + 0.08 * math.sin(2 * math.pi * 340 * t + 0.4)
            + 0.05 * math.sin(2 * math.pi * 920 * t) * (0.55 + 0.45 * math.sin(2 * math.pi * 18 * t))
            + 0.04 * math.sin(2 * math.pi * 1450 * t) * (0.5 + 0.5 * math.sin(2 * math.pi * 27 * t))
        )
        fade = 1.0
        if i < edge:
            fade = i / edge
        elif i > n - edge:
            fade = (n - i) / edge
        samples.append(max(-1.0, min(1.0, v * fade * 0.85)))

    wav_path = OUT.with_suffix(".wav")
    with wave.open(str(wav_path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in samples))

    ff = imageio_ffmpeg.get_ffmpeg_exe()
    subprocess.check_call(
        [ff, "-y", "-i", str(wav_path), "-c:a", "libvorbis", "-q:a", "4", str(OUT)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    wav_path.unlink(missing_ok=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
