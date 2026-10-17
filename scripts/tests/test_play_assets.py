import hashlib
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_play_assets import verify_assets


class PlayAssetsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        lines = []
        for suffix in ("release.aab", "mapping.txt"):
            name = f"interval-coach-1.8.5-phone-{suffix}"
            content = suffix.encode("utf-8")
            (self.root / name).write_bytes(content)
            lines.append(f"{hashlib.sha256(content).hexdigest()}  {name}")
        self.manifest = self.root / "SHA256SUMS"
        self.manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def test_downloaded_assets_match_the_published_checksums(self):
        self.assertTrue(verify_assets(self.root, "1.8.5"))

    def test_modified_bundle_is_rejected(self):
        (self.root / "interval-coach-1.8.5-phone-release.aab").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
            verify_assets(self.root, "1.8.5")

    def test_missing_mapping_is_rejected(self):
        (self.root / "interval-coach-1.8.5-phone-mapping.txt").unlink()
        with self.assertRaisesRegex(ValueError, "Missing regular release asset"):
            verify_assets(self.root, "1.8.5")

    def test_only_exact_expected_assets_are_read(self):
        with self.manifest.open("a", encoding="utf-8") as stream:
            stream.write(f"{'0' * 64}  ../../outside-checkout\n")
        self.assertTrue(verify_assets(self.root, "1.8.5"))

    def test_duplicate_checksum_entries_are_rejected(self):
        self.manifest.write_text(self.manifest.read_text() * 2, encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            verify_assets(self.root, "1.8.5")

    def test_version_cannot_escape_the_asset_directory(self):
        for version in ("../1.8.5", "1.8.5\n", "v1.8.5", "1.8.5/../../main"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                verify_assets(self.root, version)


if __name__ == "__main__":
    unittest.main()
