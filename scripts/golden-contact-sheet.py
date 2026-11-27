#!/usr/bin/env python3
"""Build a visual audit sheet from an existing, SHA-verified golden manifest."""
import argparse
from hashlib import sha256
import json
from pathlib import Path

from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("baselines", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    scenes = json.loads((args.baselines / "manifest.json").read_text())["scenes"]
    columns, cell_w, cell_h, margin = 5, 208, 350, 14
    sheet = Image.new("RGB", (columns * cell_w, ((len(scenes) + columns - 1) // columns) * cell_h), "#e7e4dd")
    draw = ImageDraw.Draw(sheet)
    for index, scene in enumerate(scenes):
        file = args.baselines / (scene["id"] + ".png")
        assert sha256(file.read_bytes()).hexdigest() == scene["pngSHA256"], f"{file.name}: PNG digest mismatch"
        with Image.open(file) as image:
            assert image.size == (scene["width"], scene["height"]), f"{file.name}: viewport mismatch"
            argb = b"".join(bytes((a, r, g, b)) for r, g, b, a in image.convert("RGBA").getdata())
            assert sha256(argb).hexdigest() == scene["pixelSHA256"], f"{file.name}: pixel digest mismatch"
            image = image.convert("RGB")
            image.thumbnail((cell_w - margin * 2, cell_h - margin - 24), Image.Resampling.LANCZOS)
            x = (index % columns) * cell_w
            y = (index // columns) * cell_h
            sheet.paste(image, (x + (cell_w - image.width) // 2, y + margin))
            draw.text((x + margin, y + cell_h - 18), scene["id"], fill="#1d251f")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)
    print(f"Verified PNG and ARGB digests for {len(scenes)} images; wrote {args.output}")


if __name__ == "__main__":
    main()
