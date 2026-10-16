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

# The adaptive launcher layers scale the 256-unit artwork down to LAUNCHER_MARK_DP and centre it in
# the 108 dp viewport. The mark is smaller than the viewport on purpose: launchers crop to a 66 dp
# safe zone and every mask shape (circle, squircle, rounded square) cuts in from there, so artwork
# that fills the 108 dp canvas loses its edges on a real home screen.
LAUNCHER_VIEWPORT_DP = 108
LAUNCHER_MARK_DP = 50
LAUNCHER_SCALE = LAUNCHER_MARK_DP / 256  # 0.1953125
LAUNCHER_OFFSET_DP = (LAUNCHER_VIEWPORT_DP - LAUNCHER_MARK_DP) / 2  # 29


def parse_xml(path: Path) -> ET.Element:
    return ET.fromstring(READER.read_bytes(path))


WEAR_DRAWABLE = ROOT / "wear/src/main/res/drawable"

try:
    source = parse_xml(ROOT / "docs/brand/cadence.svg")
    assert source.get("viewBox") == "0 0 256 256"
    expected = source.find(f"{SVG}path").get("d")
    assert source.find(f"{SVG}path").get("fill") == "#111111"

    # (drawable directory, names to verify there)
    icon_sets = [
        (DRAWABLE, ("ic_pulse", "ic_launcher_foreground", "ic_launcher_monochrome", "ic_stat_pulse", "splash_mark")),
        # The watch APK ships its own launcher icons; they must use the same supplied geometry.
        (WEAR_DRAWABLE, ("ic_launcher_foreground", "ic_launcher_monochrome")),
    ]
    for drawable_dir, names in icon_sets:
        for name in names:
            vector = parse_xml(drawable_dir / f"{name}.xml")
            paths = vector.findall(".//path")
            assert len(paths) == 1, f"{name}: expected one compound path"
            assert paths[0].get(ANDROID + "pathData") == expected, f"{name}: logo geometry drift"
            if name.startswith("ic_launcher_"):
                group = vector.find("group")
                assert vector.get(ANDROID + "viewportWidth") == str(LAUNCHER_VIEWPORT_DP)
                assert vector.get(ANDROID + "viewportHeight") == str(LAUNCHER_VIEWPORT_DP)
                for axis in ("scaleX", "scaleY"):
                    value = float(group.get(ANDROID + axis))
                    assert abs(value - LAUNCHER_SCALE) < 1e-6, f"{name}: {axis} must be {LAUNCHER_SCALE}"
                for axis in ("translateX", "translateY"):
                    value = float(group.get(ANDROID + axis))
                    assert (
                        abs(value - LAUNCHER_OFFSET_DP) < 1e-6
                    ), f"{name}: {axis} must centre the {LAUNCHER_MARK_DP} dp mark, i.e. {LAUNCHER_OFFSET_DP}"

    # Android's bitmap drawable cannot inflate a vector as its source on older releases.
    splash = parse_xml(DRAWABLE / "splash_background.xml")
    assert not splash.findall(".//bitmap"), "Use a drawable item for the splash vector"
    assert any(item.get(ANDROID + "drawable") == "@drawable/splash_mark" for item in splash)
except RepositoryIOError as error:
    print(f"unsafe brand-check input: {error}", file=sys.stderr)
    sys.exit(2)

print(
    "OK — seven brand vectors match the supplied SVG; launcher layers centre a "
    f"{LAUNCHER_MARK_DP} dp mark in the {LAUNCHER_VIEWPORT_DP} dp viewport; legacy splash is valid"
)
