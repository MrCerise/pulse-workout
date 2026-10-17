import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_privacy_policy as generator


class PrivacyPolicyTest(unittest.TestCase):
    def test_phone_and_watch_contain_identical_translated_policy(self):
        policies = json.loads((generator.ROOT / "legal/privacy.json").read_text(encoding="utf-8"))
        generated = dict(generator.outputs())
        for language, policy in policies.items():
            folder = "values" if language == "en" else f"values-{language}"
            phone = generated[generator.ROOT / "app/src/main/res" / folder / "privacy.xml"]
            watch = generated[generator.ROOT / "wear/src/main/res" / folder / "privacy.xml"]
            self.assertEqual(phone, watch)
            root = ET.fromstring(phone)
            for name, column in (("privacy_section_titles", 0), ("privacy_section_bodies", 1)):
                texts = [item.text.replace("\\'", "'").replace('\\"', '"').replace("\\\\", "\\")
                         for item in root.find(f"string-array[@name='{name}']")]
                self.assertEqual(texts, [section[column] for section in policy["sections"]])

    def test_public_pages_contain_every_policy_paragraph_without_external_scripts(self):
        import html
        policies = json.loads((generator.ROOT / "legal/privacy.json").read_text(encoding="utf-8"))
        generated = dict(generator.outputs())
        for language, policy in policies.items():
            name = "index.html" if language == "en" else f"{language}.html"
            page = generated[generator.ROOT / "docs/privacy" / name]
            for title, body in policy["sections"]:
                self.assertIn(f"<h2>{html.escape(title)}</h2>", page)
                self.assertIn(f"<p>{html.escape(body)}</p>", page)
            self.assertIn('href="index.html"', page)
            self.assertIn('href="fr.html"', page)
            self.assertNotIn("<script", page)

    def test_check_rejects_stale_generated_policy(self):
        # Prove that CI detects drift instead of silently rewriting a changed public policy.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "legal").mkdir()
            script = root / "scripts/generate_privacy_policy.py"
            script.write_bytes(Path(generator.__file__).read_bytes())
            (root / "legal/privacy.json").write_bytes((generator.ROOT / "legal/privacy.json").read_bytes())
            subprocess.run([sys.executable, str(script)], check=True, capture_output=True)
            page = root / "docs/privacy/fr.html"
            page.write_text("stale policy", encoding="utf-8")
            result = subprocess.run([sys.executable, str(script), "--check"], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("fr.html", result.stderr)
            self.assertEqual("stale policy", page.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
