"""Verify that every shipped brand mark uses the supplied SVG geometry."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app/src/main/res/drawable"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"

source = ET.parse(ROOT / "docs/brand/pulse.svg").getroot()
assert source.get("viewBox") == "0 0 256 256"
expected = source.find(f"{SVG}path").get("d")
assert source.find(f"{SVG}path").get("fill") == "#111111"

for name in ("ic_pulse", "ic_launcher_foreground", "ic_launcher_monochrome", "ic_stat_pulse", "splash_mark"):
    vector = ET.parse(DRAWABLE / f"{name}.xml").getroot()
    paths = vector.findall(".//path")
    assert len(paths) == 1, f"{name}: expected one compound path"
    assert paths[0].get(ANDROID + "pathData") == expected, f"{name}: logo geometry drift"
    if name.startswith("ic_launcher_"):
        group = vector.find("group")
        assert vector.get(ANDROID + "viewportWidth") == "108"
        assert group.get(ANDROID + "scaleX") == group.get(ANDROID + "scaleY") == "0.234375"
        assert group.get(ANDROID + "translateX") == group.get(ANDROID + "translateY") == "24"

# Android's bitmap drawable cannot inflate a vector as its source on older releases.
splash = ET.parse(DRAWABLE / "splash_background.xml").getroot()
assert not splash.findall(".//bitmap"), "Use a drawable item for the splash vector"
assert any(item.get(ANDROID + "drawable") == "@drawable/splash_mark" for item in splash)
print("OK — five brand vectors match the supplied SVG; launcher inset and legacy splash are valid")
