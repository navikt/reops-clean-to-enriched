"""Hermetic release-resolution tests: local Git fixtures, no network/deployment."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("resolve-release.sh").resolve()
IMAGE = "europe-north1-docker.pkg.dev/test/images/app:release@sha256:" + "a" * 64


class ReleaseResolutionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repo = Path(self.temporary.name)
        self.output = self.repo / "github-output"
        self.sequence = 0
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Release fixture")
        self.git("config", "user.email", "release@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "tag.gpgsign", "false")
        self.old = self.commit("old")
        self.new = self.commit("new")

    def git(self, *args, env=None):
        result = subprocess.run(
            ["git", *args], cwd=self.repo, env=env,
            check=True, text=True, capture_output=True,
        )
        return result.stdout.strip()

    def commit(self, content):
        (self.repo / "application").write_text(content)
        (self.repo / "manifest").write_text(content + "-manifest")
        self.git("add", "application", "manifest")
        self.git("commit", "-m", content)
        return self.git("rev-parse", "HEAD")

    def tag(self, name, sha=None, image=IMAGE):
        self.sequence += 1
        env = dict(os.environ, GIT_COMMITTER_DATE=f"2001-01-01T00:00:{self.sequence:02d}+0000")
        args = ["tag", "-a", name, sha or self.new, "-m", "Release fixture"]
        if image is not None:
            args += ["-m", f"Image: {image}"]
        self.git(*args, env=env)

    def resolve(self, mode="promote", environment="prod", tag="", event="workflow_dispatch", success=True):
        self.output.write_text("")
        env = dict(
            os.environ, RELEASE_MODE=mode, ENVIRONMENT=environment,
            RELEASE_TAG=tag, GITHUB_EVENT_NAME=event, GITHUB_OUTPUT=str(self.output),
        )
        result = subprocess.run(
            ["bash", str(SCRIPT)], cwd=self.repo, env=env, text=True, capture_output=True,
        )
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual(self.output.read_text(), "")
        outputs = dict(line.split("=", 1) for line in self.output.read_text().splitlines())
        return outputs, result.stdout + result.stderr

    def test_promotion_selects_latest_dev_tag_by_creation_not_name(self):
        self.tag("dev-z", self.old)
        self.tag("dev-a", self.new)
        self.tag("prod-unrelated", self.old)
        outputs, _ = self.resolve()
        self.assertEqual(outputs, {"sha": self.new, "tag": "dev-a", "image": IMAGE})

    def test_explicit_previous_dev_and_prod_images_can_be_promoted(self):
        for prefix in ("dev", "prod"):
            with self.subTest(prefix=prefix):
                self.tag(f"{prefix}-previous", self.old)
                self.tag(f"{prefix}-current", self.new)
                outputs, _ = self.resolve(tag=f"{prefix}-previous")
                self.assertEqual(outputs["sha"], self.old)
                self.assertEqual(outputs["image"], IMAGE)
                self.assertEqual((self.repo / "application").read_text(), "old")
                self.assertEqual((self.repo / "manifest").read_text(), "old-manifest")

    def test_dev_redeployment_uses_recorded_image(self):
        self.tag("dev-previous", self.old)
        outputs, _ = self.resolve("redeploy", "dev", "dev-previous")
        self.assertEqual(outputs["sha"], self.old)
        self.assertEqual(outputs["image"], IMAGE)

    def test_scheduled_refresh_uses_each_environment_tag_not_main(self):
        self.tag("dev-release", self.old)
        self.tag("prod-release", self.old)
        for environment in ("dev", "prod"):
            with self.subTest(environment=environment):
                outputs, _ = self.resolve("rebuild", environment, event="schedule")
                self.assertEqual(outputs["sha"], self.old)
                self.assertNotIn("image", outputs)
                self.assertEqual((self.repo / "manifest").read_text(), "old-manifest")

    def test_explicit_old_refresh_does_not_change_default_release(self):
        for environment in ("dev", "prod"):
            with self.subTest(environment=environment):
                self.tag(f"{environment}-old", self.old)
                self.tag(f"{environment}-new", self.new)
                tags_before = self.git("show-ref", "--tags")
                outputs, _ = self.resolve("rebuild", environment, f"{environment}-old")
                self.assertEqual(outputs["sha"], self.old)
                outputs, _ = self.resolve("rebuild", environment, event="schedule")
                self.assertEqual(outputs["sha"], self.new)
                self.assertEqual(self.git("show-ref", "--tags"), tags_before)

    def test_new_prod_rollback_tag_keeps_rolled_back_code_on_next_refresh(self):
        self.tag("prod-old", self.old)
        self.tag("prod-new", self.new)
        outputs, _ = self.resolve(tag="prod-old")
        self.tag("prod-rollback", outputs["sha"], outputs["image"])
        outputs, _ = self.resolve("rebuild", "prod", event="schedule")
        self.assertEqual(outputs["sha"], self.old)

    def test_schedule_without_tags_skips_but_manual_operations_fail(self):
        for environment in ("dev", "prod"):
            outputs, log = self.resolve("rebuild", environment, event="schedule")
            self.assertEqual(outputs, {})
            self.assertIn("::notice::", log)
            self.assertEqual(self.git("rev-parse", "HEAD"), self.new)
            self.resolve("rebuild", environment, success=False)
        self.resolve(success=False)
        self.resolve("redeploy", "dev", success=False)

    def test_explicit_missing_tag_fails_even_on_schedule(self):
        self.resolve("rebuild", "prod", "prod-missing", event="schedule", success=False)

    def test_invalid_refs_and_branches_are_rejected(self):
        self.tag("dev-good")
        self.git("branch", "dev-branch")
        for tag in ("main", "dev-branch", "dev-missing", "dev-good^{commit}",
                    "refs/tags/dev-good", "dev-../bad", "dev-good\nimage=injected"):
            with self.subTest(tag=tag):
                self.resolve(tag=tag, success=False)

    def test_wrong_environment_and_lightweight_tags_are_rejected(self):
        self.tag("prod-good")
        self.tag("dev-good")
        self.git("tag", "dev-lightweight")
        self.resolve("redeploy", "dev", "prod-good", success=False)
        self.resolve("rebuild", "dev", "prod-good", success=False)
        self.resolve("rebuild", "prod", "dev-good", success=False)
        self.resolve(tag="dev-lightweight", success=False)
        self.resolve("rebuild", "dev", "dev-lightweight", success=False)
        self.resolve("promote", "dev", success=False)

    def test_image_operations_require_one_valid_registry_image(self):
        for index, image in enumerate((None, "", "ghcr.io/elsewhere/app:tag", "not an image",
                                       IMAGE + "\nImage: " + IMAGE)):
            tag = f"dev-invalid-{index}"
            self.tag(tag, image=image)
            self.resolve(tag=tag, success=False)
            self.resolve("redeploy", "dev", tag, success=False)
        # A rebuild produces its own image, so old image metadata is unnecessary.
        outputs, _ = self.resolve("rebuild", "dev", "dev-invalid-0")
        self.assertEqual(outputs["sha"], self.new)


if __name__ == "__main__":
    unittest.main()
