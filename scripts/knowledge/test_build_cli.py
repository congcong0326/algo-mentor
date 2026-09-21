"""验证构建复用不会把过期或失败成品当作可用工具。"""

from contextlib import redirect_stdout
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from build_cli import CACHE_DIR, ensure_build


class BuildCliTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "backend/api/src/main/java/Example.java"
        self.source.parent.mkdir(parents=True)
        self.source.write_text("source")
        self.pom = self.root / "backend/pom.xml"
        self.pom.write_text("pom")
        self.jar = self.root / "backend/api/target/api.jar"
        self.command = [sys.executable, "-c", (
            "from pathlib import Path; "
            "p=Path('backend/api/target/api.jar'); "
            "p.parent.mkdir(parents=True, exist_ok=True); p.write_text('jar')"
        )]

    def build(self, force=False, command=None, toolchain="java17"):
        with redirect_stdout(io.StringIO()):
            return ensure_build(self.root, self.jar, command or self.command, force, toolchain)

    def test_content_only_changes_and_timestamps_reuse_jar(self):
        self.assertTrue(self.build())
        stamp = self.jar.stat().st_mtime_ns
        content = self.root / "knowledge-base/Java.node/outline.json"
        content.parent.mkdir(parents=True)
        content.write_text("{}")
        self.source.touch()
        self.assertFalse(self.build())
        self.assertEqual(stamp, self.jar.stat().st_mtime_ns)

    def test_source_add_edit_delete_and_pom_changes_invalidate(self):
        self.build()
        self.source.write_text("changed")
        self.assertTrue(self.build())
        added = self.source.with_name("Added.java")
        added.write_text("added")
        self.assertTrue(self.build())
        added.unlink()
        self.assertTrue(self.build())
        self.pom.write_text("new dependency")
        self.assertTrue(self.build())

    def test_resources_maven_config_toolchain_and_force_invalidate(self):
        self.build()
        resource = self.root / "backend/api/src/main/resources/config.yml"
        resource.parent.mkdir(parents=True)
        resource.write_text("resource")
        self.assertTrue(self.build())
        config = self.root / ".mvn/maven.config"
        config.parent.mkdir()
        config.write_text("-Pexample")
        self.assertTrue(self.build())
        self.assertTrue(self.build(toolchain="java21"))
        self.assertTrue(self.build(force=True, toolchain="java21"))

    def test_missing_or_modified_jar_and_corrupt_cache_invalidate(self):
        self.build()
        self.jar.write_text("other build")
        self.assertTrue(self.build())
        self.jar.unlink()
        self.assertTrue(self.build())
        (self.root / CACHE_DIR / "build.json").write_text("invalid")
        self.assertTrue(self.build())

    def test_failed_build_does_not_leave_success_stamp(self):
        self.build()
        with self.assertRaises(subprocess.CalledProcessError):
            self.build(command=[sys.executable, "-c", "raise SystemExit(2)"])
        self.assertFalse((self.root / CACHE_DIR / "build.json").exists())
        self.assertTrue(self.build())

    def test_source_changed_during_build_is_not_cached(self):
        self.build()
        changed = self.command.copy()
        changed[-1] += "; Path('backend/api/src/main/java/Example.java').write_text('during build')"
        with self.assertRaisesRegex(ValueError, "构建期间"):
            self.build(command=changed)
        self.assertFalse((self.root / CACHE_DIR / "build.json").exists())


if __name__ == "__main__":
    unittest.main()
