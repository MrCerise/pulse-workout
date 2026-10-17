from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_release_source import validate_source


class ReleaseSourceTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.repository = Path(directory.name)
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Release source test")
        self.git("config", "user.email", "release-test@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "core.hooksPath", str(self.repository / "no-hooks"))
        self.old = self.commit("original release")
        self.git("tag", "v1.8.4")
        self.current = self.commit("new main commit")
        self.git("update-ref", "refs/remotes/origin/main", self.current)

    def git(self, *arguments):
        return subprocess.run(
            ["git", *arguments], cwd=self.repository, check=True,
            capture_output=True, text=True, timeout=20,
        ).stdout.strip()

    def commit(self, content):
        (self.repository / "source.txt").write_text(content, encoding="utf-8")
        self.git("add", "source.txt")
        self.git("commit", "-m", content)
        return self.git("rev-parse", "HEAD")

    def test_main_can_create_a_new_release_tag(self):
        self.assertEqual(validate_source(self.repository, self.current, "", "v1.8.5"), self.current)

    def test_annotated_and_lightweight_tags_can_select_the_workflow_commit(self):
        self.git("tag", "v1.8.5")
        self.git("tag", "-a", "v1.8.5-rc.1", "-m", "Release candidate")
        for tag in ("v1.8.5", "v1.8.5-rc.1"):
            with self.subTest(tag=tag):
                self.assertEqual(validate_source(self.repository, self.current, tag, tag), self.current)

    def test_an_older_tag_builds_when_selected_as_the_workflow_ref(self):
        self.git("checkout", "--detach", self.old)
        self.assertEqual(validate_source(self.repository, self.old, "v1.8.4", "v1.8.4"), self.old)

    def test_input_cannot_switch_main_to_an_older_tag(self):
        with self.assertRaisesRegex(ValueError, "Use workflow from"):
            validate_source(self.repository, self.current, "v1.8.4", "v1.8.4")

    def test_published_tag_cannot_be_overwritten_with_another_commit(self):
        with self.assertRaisesRegex(ValueError, "Published release tag points to another commit"):
            validate_source(self.repository, self.current, "", "v1.8.4")

    def test_unmerged_feature_commit_cannot_release_even_with_a_matching_tag(self):
        self.git("checkout", "-b", "feature")
        feature = self.commit("unreviewed code")
        self.git("tag", "v9.0.0")
        with self.assertRaisesRegex(ValueError, "already merged into main"):
            validate_source(self.repository, feature, "v9.0.0", "v9.0.0")

    def test_changed_checkout_does_not_match_the_workflow_event(self):
        with self.assertRaisesRegex(ValueError, "does not match"):
            validate_source(self.repository, self.old, "", "v1.8.5")

    def test_invalid_or_missing_tag_is_rejected(self):
        for tag in ("missing", "../main", "v1.8.5\nsha=bad"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                validate_source(self.repository, self.current, tag, tag)

    def test_source_must_be_a_full_sha(self):
        for sha in ("main", "HEAD", self.current[:7], self.current + "\n"):
            with self.subTest(sha=sha), self.assertRaises(ValueError):
                validate_source(self.repository, sha, "", "v1.8.5")


if __name__ == "__main__":
    unittest.main()
