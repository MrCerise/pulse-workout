import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import check_ui
from scripts.safe_repo_io import BoundedSourceReader


class TranslationChecksTest(unittest.TestCase):
    def check(self, source: str, translation: str) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder, text in [("values", source), ("values-fr-rFR", translation)]:
                path = root / folder / "strings.xml"
                path.parent.mkdir()
                path.write_text(f"<resources>{text}</resources>", encoding="utf-8")
            with patch.object(check_ui, "SOURCE_READER", BoundedSourceReader(root)):
                return check_ui.check_translation_tables(root)

    def test_missing_translations_and_english_copies_fail(self):
        self.assertTrue(self.check('<string name="title">Language</string>', ""))
        sentence = '<string name="body">Choose your next workout.</string>'
        self.assertTrue(any("untranslated" in issue for issue in self.check(sentence, sentence)))

    def test_shared_words_and_reordered_arguments_are_valid(self):
        self.assertEqual([], self.check(
            '<string name="brand">Interval Coach</string><string name="row">%1$d rounds: %2$s</string>',
            '<string name="brand">Interval Coach</string><string name="row">%2$s : %1$d tours</string>',
        ))

    def test_formatter_types_must_match(self):
        self.assertTrue(self.check(
            '<string name="count">%1$d sessions</string>',
            '<string name="count">%1$s séances</string>',
        ))

    def test_every_plural_form_preserves_arguments(self):
        source = '<plurals name="count"><item quantity="one">%1$d session</item><item quantity="other">%1$d sessions</item></plurals>'
        translation = '<plurals name="count"><item quantity="one">%1$d séance</item><item quantity="other">%1$d séances</item></plurals>'
        self.assertEqual([], self.check(source, translation))
        self.assertTrue(self.check(source, translation.replace('%1$d séances', 'séances')))
        self.assertTrue(self.check(source, translation.replace('quantity="other"', 'quantity="many"')))


if __name__ == "__main__":
    unittest.main()
