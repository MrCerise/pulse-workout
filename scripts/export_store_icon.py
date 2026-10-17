#!/usr/bin/env python3
"""Export the existing vector logo; requires Pillow and resvg-py."""
from io import BytesIO
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image
from resvg_py import svg_to_bytes

ROOT = Path(__file__).resolve().parent.parent
SVG_NS = "http://www.w3.org/2000/svg"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def main():
    source = ROOT / "docs/brand/pulse.svg"
    artwork = ET.parse(source).getroot().find(f"{{{SVG_NS}}}path")
    background = ET.parse(
        ROOT / "app/src/main/res/drawable/ic_launcher_background.xml"
    ).getroot().find("solid").get(ANDROID_NS + "color")

    # Keep the supplied geometry and launcher colours. Play adds its own corner
    # mask and outer shadow, so this export is square with a full background.
    canvas = ET.Element("svg", {
        "xmlns": SVG_NS, "viewBox": "0 0 512 512", "width": "512", "height": "512",
    })
    ET.SubElement(canvas, "title").text = "Interval Coach"
    ET.SubElement(canvas, "rect", {"width": "512", "height": "512", "fill": background})
    group = ET.SubElement(canvas, "g", {"transform": "translate(64 64) scale(1.5)"})
    ET.SubElement(group, "path", dict(artwork.attrib))
    pixels = svg_to_bytes(svg_string=ET.tostring(canvas, encoding="unicode"),
                          width=2048, height=2048, skip_system_fonts=True)
    icon = Image.open(BytesIO(pixels)).convert("RGBA")
    icon = icon.resize((512, 512), Image.Resampling.LANCZOS)
    destination = ROOT / "docs/store/icon.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    icon.save(destination, optimize=True)

    transparent = svg_to_bytes(svg_path=str(source), width=1024, height=1024,
                               skip_system_fonts=True)
    logo = Image.open(BytesIO(transparent)).convert("RGBA")
    logo_path = ROOT / "docs/brand/interval-coach-logo.png"
    logo.save(logo_path, optimize=True)
    for path in (destination, logo_path):
        with Image.open(path) as image:
            print(f"{path.relative_to(ROOT)}: {image.size}, {image.mode}, {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
