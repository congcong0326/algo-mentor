#!/usr/bin/env python3
"""生成与检查编辑大纲；正式内容仍由 Java 目录导入器解析。"""

import argparse
import json
import os
import sys
from pathlib import Path

NODE_SUFFIX = ".node"
CONTENT_SUFFIXES = (NODE_SUFFIX, ".card.md", ".article.md")
SPEC_NAME = "outline.json"
MAINTENANCE_DIRS = {"references", "skills"}


def marked(path):
    return path.name.endswith(CONTENT_SUFFIXES)


def read_json(path):
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"重复 JSON 字段：{key}")
            result[key] = value
        return result

    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_pairs)


def expected_paths(spec):
    document = read_json(spec)
    if (not isinstance(document, dict) or set(document) != {"version", "children"}
            or type(document["version"]) is not int or document["version"] != 1):
        raise ValueError(f"{spec}：清单只支持 version: 1 与 children")
    expected = set()

    def visit(children, parent):
        if not isinstance(children, list):
            raise ValueError(f"{spec}：children 必须是数组")
        siblings = set()
        for node in children:
            if (not isinstance(node, dict) or "title" not in node
                    or set(node) - {"title", "children"}):
                raise ValueError(f"{spec}：节点只支持 title 和 children")
            title = node["title"]
            if (not isinstance(title, str) or not title or title != title.strip()
                    or title in {".", ".."} or len(title) > 200
                    or any(c in "/\\" or ord(c) < 32 or ord(c) == 127 for c in title)):
                raise ValueError(f"非法标题 {title!r}：禁止路径分隔符、控制字符和首尾空白；I/O 请明确改为 IO")
            if title in siblings:
                raise ValueError(f"{spec}：同级标题重复：{title}")
            siblings.add(title)
            path = parent / (title + NODE_SUFFIX)
            if (len(os.fsencode(path.name)) > os.pathconf(spec.parent, "PC_NAME_MAX")
                    or len(os.fsencode(spec.parent / path)) >= os.pathconf(spec.parent, "PC_PATH_MAX")):
                raise ValueError(f"非法标题 {title!r}：文件名或完整路径超出文件系统长度限制")
            expected.add(path)
            visit(node.get("children", []), path)

    visit(document["children"], Path())
    if not expected:
        raise ValueError(f"{spec}：大纲清单不能为空")
    return expected


def scan(root):
    """只遍历正式节点；诊断被普通目录隔断的内容，不跟随符号链接。"""
    nodes, specs, errors = set(), [], []

    def hidden(directory):
        for path in sorted(directory.iterdir()):
            if marked(path):
                errors.append(f"漏扫路径：{path}；父路径含普通目录，请改为连续 .node 目录")
            elif path.is_dir() and not path.is_symlink():
                hidden(path)

    def visit(directory):
        for path in sorted(directory.iterdir()):
            if path.name == SPEC_NAME and directory != root:
                if path.is_symlink() or not path.is_file():
                    errors.append(f"清单必须是普通文件：{path}")
                else:
                    specs.append(path)
            elif marked(path):
                if path.is_symlink():
                    errors.append(f"正式内容不支持符号链接：{path}")
                elif path.name.endswith(NODE_SUFFIX):
                    if not path.is_dir():
                        errors.append(f".node 必须是目录：{path}")
                    else:
                        nodes.add(path)
                        visit(path)
                elif not path.is_file():
                    errors.append(f"内容必须是普通文件：{path}")
            elif (directory != root and path.is_dir() and not path.is_symlink()
                  and path.name not in MAINTENANCE_DIRS):
                hidden(path)

    if root.is_symlink() or not root.is_dir():
        raise ValueError(f"知识库根目录不存在或为符号链接：{root}")
    visit(root)
    if errors:
        raise ValueError("\n".join(errors))
    return nodes, specs


def compare(spec, nodes):
    expected = {spec.parent / path for path in expected_paths(spec)}
    actual = {p for p in nodes if spec.parent in p.parents}
    return expected, expected - actual, actual - expected


def run(root, action, spec=None):
    # abspath 不解析符号链接，让检查仍能识别链接本身。
    root = Path(os.path.abspath(root))
    nodes, specs = scan(root)
    if spec is not None:
        spec = Path(os.path.abspath(spec))
        if spec not in specs:
            raise ValueError(f"清单必须是正式 .node 目录中的 {SPEC_NAME}：{spec}")
        specs = [spec]
    if action == "apply" and spec is None:
        raise ValueError("生成目录必须通过 --outline 指定一份清单")
    plans = [(path, *compare(path, nodes)) for path in specs]
    for path, expected, missing, extra in plans:
        print(f"{path.relative_to(root)}：预期 {len(expected)} 个后代节点，缺少 {len(missing)}，额外 {len(extra)}")
        for label, paths in [("待创建", missing), ("清单外节点（不自动删除）", extra)]:
            for entry in sorted(paths):
                print(f"  {label}：{entry.relative_to(root)}")
    if action == "apply":
        if any(extra for _, _, _, extra in plans):
            raise ValueError("存在清单外节点；请先明确迁移内容或更新清单，再生成目录")
        for _, expected, _, _ in plans:
            for path in sorted(expected):
                path.mkdir(parents=True, exist_ok=True)
            for path in sorted(expected):
                if not any(path.iterdir()):
                    (path / ".gitkeep").touch()
        print("生成完成；已有文件保持不变。")
    elif action == "check" and any(missing or extra for _, _, missing, extra in plans):
        raise ValueError("大纲与目录不一致，请先预览并修正")
    else:
        print(f"扫描完成：{len(nodes)} 个节点，{len(specs)} 份大纲清单。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["preview", "apply", "check"])
    parser.add_argument("--root", type=Path, default=Path("knowledge-base"))
    parser.add_argument("--outline", type=Path)
    args = parser.parse_args()
    try:
        run(args.root, args.action, args.outline)
    except (ValueError, OSError) as error:
        print(f"大纲检查失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
