#!/usr/bin/env python3
"""Keep the offline phone/watch policy and public HTML identical. No dependencies."""
import argparse
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def android_text(value):
    # Android's resource parser interprets apostrophes, quotes and escapes after XML parsing.
    return html.escape(value, quote=False).replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')


def outputs():
    policies = json.loads((ROOT / "legal/privacy.json").read_text(encoding="utf-8"))
    for language, policy in policies.items():
        strings = {
            "privacy_policy_title": policy["title"],
            "privacy_updated": policy["updated"],
            "privacy_back": policy["back"],
            "privacy_location_title": policy["location_title"],
            "privacy_location_enable": policy["location_enable"],
            "privacy_location_skip": policy["location_skip"],
            "privacy_health_notice": policy["health_notice"],
            "privacy_microphone_notice": policy["microphone_notice"],
            "privacy_location_notice": policy["location_notice"],
            "privacy_medical_disclaimer": policy["medical"],
        }
        rows = ['<?xml version="1.0" encoding="utf-8"?>',
                '<!-- Generated from legal/privacy.json; run scripts/generate_privacy_policy.py. -->',
                '<resources>']
        rows += [f'    <string name="{key}" formatted="false">{android_text(value)}</string>'
                 for key, value in strings.items()]
        for name, index in (("privacy_section_titles", 0), ("privacy_section_bodies", 1)):
            rows.append(f'    <string-array name="{name}">')
            rows += [f'        <item>{android_text(section[index])}</item>' for section in policy["sections"]]
            rows.append('    </string-array>')
        xml = "\n".join(rows + ['</resources>', ''])
        folder = "values" if language == "en" else f"values-{language}"
        for module in ("app", "wear"):
            yield ROOT / module / "src/main/res" / folder / "privacy.xml", xml

        sections = "\n".join(
            f'<section><h2>{html.escape(title)}</h2><p>{html.escape(body)}</p></section>'
            for title, body in policy["sections"]
        )
        page = f'''<!doctype html>
<!-- Generated from legal/privacy.json; run scripts/generate_privacy_policy.py. -->
<html lang="{language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Interval Coach — {html.escape(policy["title"])}</title>
<style>
body{{font:1rem/1.7 system-ui,sans-serif;max-width:48rem;margin:auto;padding:1.5rem;color:#17212b;background:#fff;overflow-wrap:break-word}}
h1,h2{{line-height:1.25}}h1{{font-size:2rem}}h2{{font-size:1.25rem}}
a{{color:#174ea6;text-underline-offset:.2em}}a:focus-visible{{outline:2px solid currentColor;outline-offset:4px}}
header{{border-bottom:1px solid #d5dbe1;padding-block:1.5rem}}section{{margin-block:2rem}}
nav{{text-align:right}}footer{{border-top:1px solid #d5dbe1;padding-block:1rem}}
@media print{{nav{{display:none}}body{{max-width:none;padding:0}}}}
</style>
</head>
<body>
<nav aria-label="{'Langue' if language == 'fr' else 'Language'}"><a href="index.html" lang="en" hreflang="en">English</a> · <a href="fr.html" lang="fr" hreflang="fr">Français</a></nav>
<main>
<header>
<p>Interval Coach · Android &amp; Wear OS</p>
<h1>{html.escape(policy["title"])}</h1>
<p>{html.escape(policy["updated"])}</p>
</header>
{sections}
</main>
<footer><p>{'Contact confidentialité' if language == 'fr' else 'Privacy contact'} : <a href="mailto:security@nexalab.fr">security@nexalab.fr</a></p></footer>
</body>
</html>
'''
        yield ROOT / "docs/privacy" / ("index.html" if language == "en" else f"{language}.html"), page


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail on stale or missing generated files")
    args = parser.parse_args()
    stale = []
    for path, content in outputs():
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8", newline="\n")
    if stale:
        parser.exit(1, "Regenerate privacy policy: " + ", ".join(stale) + "\n")
    print("Privacy policy: public HTML and phone/watch resources are in sync.")


if __name__ == "__main__":
    main()
