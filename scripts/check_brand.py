"""Verify that every shipped brand mark uses the supplied SVG geometry."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

try:
    from .safe_repo_io import BoundedSourceReader, RepositoryIOError
except ImportError:  # Executed as `python3 scripts/check_brand.py`.
    from safe_repo_io import BoundedSourceReader, RepositoryIOError

ROOT = Path(__file__).resolve().parent.parent
READER = BoundedSourceReader(ROOT)
DRAWABLE = ROOT / "app/src/main/res/drawable"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"


def parse_xml(path: Path) -> ET.Element:
    return ET.fromstring(READER.read_bytes(path))


try:
    source = parse_xml(ROOT / "docs/brand/pulse.svg")
    assert source.get("viewBox") == "0 0 256 256"
    expected = source.find(f"{SVG}path").get("d")
    assert source.find(f"{SVG}path").get("fill") == "#111111"

    for name in ("ic_pulse", "ic_launcher_foreground", "ic_launcher_monochrome", "ic_stat_pulse", "splash_mark"):
        vector = parse_xml(DRAWABLE / f"{name}.xml")
        paths = vector.findall(".//path")
        assert len(paths) == 1, f"{name}: expected one compound path"
        assert paths[0].get(ANDROID + "pathData") == expected, f"{name}: logo geometry drift"
        if name.startswith("ic_launcher_"):
            group = vector.find("group")
            assert vector.get(ANDROID + "viewportWidth") == "108"
            assert group.get(ANDROID + "scaleX") == group.get(ANDROID + "scaleY") == "0.234375"
            assert group.get(ANDROID + "translateX") == group.get(ANDROID + "translateY") == "24"

    # Android's bitmap drawable cannot inflate a vector as its source on older releases.
    splash = parse_xml(DRAWABLE / "splash_background.xml")
    assert not splash.findall(".//bitmap"), "Use a drawable item for the splash vector"
    assert any(item.get(ANDROID + "drawable") == "@drawable/splash_mark" for item in splash)
except RepositoryIOError as error:
    print(f"unsafe brand-check input: {error}", file=sys.stderr)
    sys.exit(2)

print("OK — five brand vectors match the supplied SVG; launcher inset and legacy splash are valid")
