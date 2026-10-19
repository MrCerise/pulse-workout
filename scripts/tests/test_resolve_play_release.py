import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from resolve_play_release import resolve_release


ROOT = Path(__file__).resolve().parents[2]
BUILD_FILE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")


class PlayReleaseTest(unittest.TestCase):
    def resolve(self, **overrides):
        env = {
            "RELEASE_CHANNEL": "internal",
            "RELEASE_VERSION_NAME": "1.8.1",
            "RELEASE_VERSION_CODE": "18001050",
            "RELEASE_STATUS": "draft",
            "RELEASE_ROLLOUT_PERCENT": "100",
        }
        env.update(overrides)
        return resolve_release(env, BUILD_FILE)

    def test_repository_default_is_read_from_actual_build_file(self):
        result = self.resolve(RELEASE_VERSION_NAME="")
        self.assertRegex(result["name"], r"^\d+\.\d+\.\d+$")
        self.assertEqual(result["full_name"], f"{result['name']}-internal.18001050")

    def test_explicit_code_is_independent_of_workflow_run_number(self):
        result = self.resolve(GITHUB_RUN_NUMBER="1000")
        self.assertEqual(result["code"], "18001050")

    def test_testing_tracks(self):
        for channel in ("internal", "beta"):
            with self.subTest(channel=channel):
                result = self.resolve(RELEASE_CHANNEL=channel, RELEASE_STATUS="completed")
                self.assertEqual(result["full_name"], f"1.8.1-{channel}.18001050")
                self.assertEqual(result["status"], "completed")
                self.assertEqual(result["fraction"], "")

    def test_full_production_release_has_no_user_fraction(self):
        result = self.resolve(RELEASE_CHANNEL="production", RELEASE_STATUS="completed")
        self.assertEqual(result["full_name"], "1.8.1")
        self.assertEqual(result["status"], "completed")
        self.assertEqual(result["fraction"], "")

    def test_staged_rollout_uses_in_progress(self):
        for percent, fraction in (("1", "0.01"), ("10", "0.10"), ("99", "0.99")):
            with self.subTest(percent=percent):
                result = self.resolve(RELEASE_CHANNEL="production", RELEASE_STATUS="completed",
                                      RELEASE_ROLLOUT_PERCENT=percent)
                self.assertEqual(result["status"], "inProgress")
                self.assertEqual(result["fraction"], fraction)

    def test_draft_and_testing_reject_staged_rollouts(self):
        for channel, status in (("production", "draft"), ("beta", "completed"), ("internal", "draft")):
            with self.subTest(channel=channel, status=status), self.assertRaises(ValueError):
                self.resolve(RELEASE_CHANNEL=channel, RELEASE_STATUS=status, RELEASE_ROLLOUT_PERCENT="10")

    def test_invalid_rollout_percent(self):
        for percent in ("0", "101", "-1", "1.5", "NaN", "", "01", "100\nstatus=completed"):
            with self.subTest(percent=percent), self.assertRaises(ValueError):
                self.resolve(RELEASE_ROLLOUT_PERCENT=percent)

    def test_invalid_version_names_and_injection(self):
        for name in ("v1.8.1", "1.8", "1.8.1-beta", "01.8.1", "1.8.1; touch /tmp/injected",
                     '$(echo injected)', "1.8.1\ncode=999"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.resolve(RELEASE_VERSION_NAME=name)

    def test_version_code_limits(self):
        for code in ("1", "2100000000"):
            with self.subTest(code=code):
                self.assertEqual(self.resolve(RELEASE_VERSION_CODE=code)["code"], code)
        for code in ("", "0", "-1", "01", "1.5", "2100000001", "99999999999999", "1\nname=bad"):
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.resolve(RELEASE_VERSION_CODE=code)

    def test_invalid_choices(self):
        for overrides in ({"RELEASE_CHANNEL": "anything"}, {"RELEASE_STATUS": "halted"}):
            with self.subTest(overrides=overrides), self.assertRaises(ValueError):
                self.resolve(**overrides)

    def test_tag_drives_the_built_version_instead_of_repository_default(self):
        result = self.resolve(RELEASE_TAG="v1.8.4", RELEASE_VERSION_NAME="",
                              RELEASE_VERSION_CODE="", GITHUB_RUN_NUMBER="190")
        self.assertEqual(result["name"], "1.8.4")
        self.assertEqual(result["full_name"], "1.8.4")
        self.assertEqual(result["code"], "10804190")
        self.assertEqual(result["track"], "internal")
        self.assertEqual(result["status"], "draft")
        self.assertEqual(result["release_tag"], "v1.8.4")
        self.assertEqual(result["prerelease"], "false")

    def test_stable_tag_does_not_reset_the_prerelease_version_code(self):
        before = self.resolve(RELEASE_TAG="v1.8.1-rc.1", RELEASE_VERSION_CODE="",
                              GITHUB_RUN_NUMBER="190")
        after = self.resolve(RELEASE_TAG="v1.8.1", RELEASE_VERSION_CODE="",
                             GITHUB_RUN_NUMBER="191")
        self.assertGreater(int(after["code"]), int(before["code"]))
        self.assertEqual(before["full_name"], "1.8.1-rc.1")
        self.assertEqual(before["prerelease"], "true")

    def test_automatic_code_never_wraps_a_workflow_run_number(self):
        for run in ("", "0", "1000", "1190", "001", "1\ncode=5"):
            with self.subTest(run=run), self.assertRaises(ValueError):
                self.resolve(RELEASE_TAG="v1.8.1", RELEASE_VERSION_CODE="", GITHUB_RUN_NUMBER=run)

    def test_explicit_code_overrides_automatic_slots(self):
        result = self.resolve(RELEASE_TAG="v1.8.1", GITHUB_RUN_NUMBER="2000")
        self.assertEqual(result["code"], "18001050")
        result = self.resolve(RELEASE_TAG="v1.100.101", RELEASE_VERSION_NAME="1.100.101")
        self.assertEqual(result["code"], "18001050")

    def test_automatic_code_bounds(self):
        for tag in ("v210.0.0", "v1.100.0", "v1.0.100"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.resolve(RELEASE_TAG=tag, RELEASE_VERSION_NAME="",
                             RELEASE_VERSION_CODE="", GITHUB_RUN_NUMBER="190")

    def test_tag_name_validation_and_version_mismatch(self):
        for tag in ("1.8.1", "v1.8", "v01.8.1", "v1.8.1;bad", "v1.8.1\ntrack=production", "v1.8.1-", "v1.8.1-rc..1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.resolve(RELEASE_TAG=tag)
        with self.assertRaises(ValueError):
            self.resolve(RELEASE_TAG="v1.8.2")

    def test_testing_releases_have_distinct_github_tags(self):
        result = self.resolve()
        self.assertEqual(result["release_tag"], "v1.8.1-internal.18001050")
        self.assertEqual(result["prerelease"], "true")
        self.assertEqual(self.resolve(RELEASE_CHANNEL="production")["release_tag"], "v1.8.1")

    def test_closed_track_id_is_explicit_and_preserved(self):
        for track in ("alpha", "PreRelease Beta", "closed-testing"):
            with self.subTest(track=track):
                result = self.resolve(RELEASE_CHANNEL="closed", RELEASE_PLAY_TRACK=track)
                self.assertEqual(result["track"], track)
                self.assertEqual(result["full_name"], "1.8.1-closed.18001050")
        for track in ("", "production", "beta", "internal", "track\ncode=99", "../production", "track\rname", "track\tname"):
            with self.subTest(track=track), self.assertRaises(ValueError):
                self.resolve(RELEASE_CHANNEL="closed", RELEASE_PLAY_TRACK=track)

    def test_track_cannot_redirect_another_channel(self):
        with self.assertRaises(ValueError):
            self.resolve(RELEASE_CHANNEL="internal", RELEASE_PLAY_TRACK="production")

    def test_missing_repository_default(self):
        with self.assertRaises(ValueError):
            resolve_release({"RELEASE_CHANNEL": "internal"}, "")

    def test_cli_writes_outputs_only_after_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "outputs.txt"
            env = dict(os.environ, RELEASE_CHANNEL="production", RELEASE_VERSION_NAME="2.0.0",
                       RELEASE_VERSION_CODE="20000001", RELEASE_STATUS="completed",
                       RELEASE_ROLLOUT_PERCENT="25", GITHUB_OUTPUT=str(output))
            command = [sys.executable, "scripts/resolve_play_release.py"]
            result = subprocess.run(command, env=env, cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("status=inProgress\nfraction=0.25\n", output.read_text())
            output.unlink()
            env["RELEASE_VERSION_NAME"] = "2.0.0\ncode=99"
            result = subprocess.run(command, env=env, cwd=ROOT, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
