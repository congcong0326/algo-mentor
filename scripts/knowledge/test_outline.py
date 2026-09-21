"""覆盖大纲误生成、漏扫和重复执行时的内容保护。"""

from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import tempfile
import unittest

from outline import compare, expected_paths, run, scan


class OutlineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.topic = self.root / "Java.node/基础.node"
        self.topic.mkdir(parents=True)
        self.spec = self.topic / "outline.json"
        self.write_spec([{"title": "对象", "children": [{"title": "值传递"}]}])

    def write_spec(self, children):
        self.spec.write_text(json.dumps({"version": 1, "children": children}))

    def execute(self, action, spec=None):
        with redirect_stdout(io.StringIO()):
            run(self.root, action, spec)

    def test_preview_is_read_only_and_apply_preserves_contents(self):
        self.execute("preview")
        self.assertFalse((self.topic / "对象.node").exists())
        self.execute("apply", self.spec)
        leaf = self.topic / "对象.node/值传递.node"
        self.assertTrue((leaf / ".gitkeep").is_file())
        card = leaf / "问题.card.md"
        card.write_text("---\nslug: stable-card\n---\n原有回答\n")
        before = {p.relative_to(self.root): p.read_bytes()
                  for p in self.root.rglob("*") if p.is_file()}
        self.execute("apply", self.spec)
        self.execute("check")
        after = {p.relative_to(self.root): p.read_bytes()
                 for p in self.root.rglob("*") if p.is_file()}
        self.assertEqual(before, after)

    def test_equal_counts_do_not_hide_wrong_paths(self):
        self.execute("apply", self.spec)
        (self.topic / "对象.node/值传递.node").rename(self.topic / "对象.node/错误.node")
        nodes, _ = scan(self.root)
        expected, missing, extra = compare(self.spec, nodes)
        self.assertEqual(len(expected), 2)
        self.assertEqual({p.name for p in missing}, {"值传递.node"})
        self.assertEqual({p.name for p in extra}, {"错误.node"})
        with self.assertRaisesRegex(ValueError, "不一致"):
            self.execute("check")
        with self.assertRaisesRegex(ValueError, "清单外节点"):
            self.execute("apply", self.spec)
        self.assertFalse((self.topic / "对象.node/值传递.node").exists())

    def test_invalid_title_rejected_before_any_writes(self):
        for title in ["I/O", "A\\B", "..", "", " 空白", "换\n行", "a" * 201, "长" * 100]:
            with self.subTest(title=title):
                self.write_spec([{"title": "有效"}, {"title": title}])
                with self.assertRaisesRegex(ValueError, "非法标题"):
                    self.execute("apply", self.spec)
                self.assertFalse((self.topic / "有效.node").exists())

    def test_schema_errors_and_duplicate_titles(self):
        for document in [
            {"version": True, "children": []},
            {"version": 2, "children": []},
            {"version": 1, "children": [], "extra": 1},
            {"version": 1, "children": [{"title": "重复"}, {"title": "重复"}]},
            {"version": 1, "children": [{"title": "主题", "typo": []}]},
            {"version": 1, "children": "错误"},
            {"version": 1, "children": []},
        ]:
            with self.subTest(document=document):
                self.spec.write_text(json.dumps(document))
                with self.assertRaises(ValueError):
                    expected_paths(self.spec)
        self.spec.write_text('{"version":1,"version":1,"children":[]}')
        with self.assertRaisesRegex(ValueError, "重复 JSON"):
            expected_paths(self.spec)

    def test_hidden_content_is_reported_but_reference_trees_are_ignored(self):
        for path in [self.root / "references/例.node", self.topic / "references/例.node",
                     self.topic / "skills/例.node"]:
            path.mkdir(parents=True)
            (path / "示例.card.md").write_text("参考，不导入")
        self.execute("preview")
        hidden = self.topic / "基础 I/O 与序列化.node"
        hidden.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, "漏扫路径.*O 与序列化"):
            self.execute("preview")

    def test_symlinks_and_file_collisions_fail_before_generation(self):
        other = self.root / "outside"
        other.mkdir()
        link = self.topic / "对象.node"
        link.symlink_to(other, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "符号链接"):
            self.execute("apply", self.spec)
        self.assertFalse(any(other.iterdir()))
        link.unlink()
        link.write_text("不可覆盖")
        with self.assertRaisesRegex(ValueError, "必须是目录"):
            self.execute("apply", self.spec)
        self.assertEqual(link.read_text(), "不可覆盖")

    def test_apply_requires_explicit_spec_and_rejects_external_spec(self):
        with self.assertRaisesRegex(ValueError, "指定一份清单"):
            self.execute("apply")
        external = self.root / "outside/outline.json"
        external.parent.mkdir()
        external.write_bytes(self.spec.read_bytes())
        with self.assertRaisesRegex(ValueError, "正式 .node"):
            self.execute("apply", external)


if __name__ == "__main__":
    unittest.main()
