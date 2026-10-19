from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_release_notes import prepare_notes


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.releases = self.root / "releases"
        self.source = self.releases / "1.8.5"
        (self.source / "whatsnew").mkdir(parents=True)
        (self.source / "github.md").write_text("# Release notes\n", encoding="utf-8")
        for locale in ("en-US", "fr-FR"):
            self.note(locale, "Update\n")
        self.destination = self.root / "prepared"

    def note(self, locale, text):
        (self.source / "whatsnew" / f"whatsnew-{locale}").write_text(text, encoding="utf-8")

    def prepare(self, version="1.8.5"):
        return prepare_notes(self.releases, version, self.destination)

    def test_stages_github_body_and_localized_play_notes(self):
        self.note("de-DE", "Aktualisierung")
        self.assertEqual(self.prepare(), 3)
        self.assertEqual((self.destination / "github.md").read_text(encoding="utf-8"), "# Release notes\n")
        self.assertEqual((self.destination / "whatsnew/whatsnew-de-DE").read_text(encoding="utf-8"), "Aktualisierung")

    def test_play_limit_counts_unicode_characters_and_exact_exported_text(self):
        self.note("fr-FR", "\u00e9" * 500 + "\n")
        self.prepare()
        exported = (self.destination / "whatsnew/whatsnew-fr-FR").read_text(encoding="utf-8")
        self.assertEqual(len(exported), 500)
        self.note("fr-FR", "\u00e9" * 501)
        with self.assertRaisesRegex(ValueError, "1 to 500"):
            self.prepare()

    def test_invalid_locale_prevents_all_artifact_writes(self):
        self.note("en-US.txt", "Wrong file extension")
        with self.assertRaisesRegex(ValueError, "filename"):
            self.prepare()
        self.assertFalse(self.destination.exists())

    def test_empty_notes_are_rejected(self):
        self.note("fr-FR", " \n ")
        with self.assertRaisesRegex(ValueError, "1 to 500"):
            self.prepare()

    def test_required_locales_are_not_silently_omitted(self):
        (self.source / "whatsnew/whatsnew-fr-FR").unlink()
        with self.assertRaisesRegex(ValueError, "English.*French"):
            self.prepare()

    def test_missing_version_or_github_body_is_rejected(self):
        with self.assertRaises(ValueError):
            self.prepare("1.8.6")
        (self.source / "github.md").unlink()
        with self.assertRaises(ValueError):
            self.prepare()

    def test_version_cannot_select_another_directory(self):
        for version in ("../1.8.5", "v1.8.5", "1.8.5\n", "1.8.5/../1.8.6"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                self.prepare(version)


if __name__ == "__main__":
    unittest.main()
