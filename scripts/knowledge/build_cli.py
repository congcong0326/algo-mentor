#!/usr/bin/env python3
"""按后端输入和成品摘要复用知识库导入工具。"""

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

CACHE_DIR = ".cache/knowledge-cli"
SKIP_DIRS = {"target", ".git", ".m2", "node_modules"}


def digest_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inputs_digest(root, command, toolchain):
    files = set()
    for directory, names, filenames in os.walk(root / "backend"):
        names[:] = [name for name in names if name not in SKIP_DIRS]
        path = Path(directory)
        parts = path.relative_to(root).parts
        is_main = any(parts[i:i + 2] == ("src", "main") for i in range(len(parts) - 1))
        for name in filenames:
            if name == "pom.xml" or is_main:
                files.add(path / name)
    if (root / "pom.xml").is_file():
        files.add(root / "pom.xml")
    for directory in [root / ".mvn", root / "backend/.mvn"]:
        if directory.is_dir():
            files.update(p for p in directory.rglob("*") if p.is_file())
    digest = hashlib.sha256()
    digest.update(json.dumps({
        "command": command,
        "toolchain": toolchain,
        "environment": {key: os.environ.get(key, "") for key in
                        ["JAVA_HOME", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"]},
        "builder": digest_file(Path(__file__)),
    }, sort_keys=True).encode())
    for path in sorted(files):
        digest.update(str(path.relative_to(root)).encode())
        digest.update(b"\0")
        digest.update(digest_file(path).encode())
    return digest.hexdigest()


def ensure_build(root, jar, command, force=False, toolchain=""):
    cache = root / CACHE_DIR
    cache.mkdir(parents=True, exist_ok=True)
    with (cache / "build.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        stamp = cache / "build.json"
        try:
            previous = json.loads(stamp.read_text())
        except (OSError, ValueError):
            previous = None
        inputs = inputs_digest(root, command, toolchain)
        current = {"inputs": inputs, "jar": digest_file(jar) if jar.is_file() else None}
        if not force and current["jar"] is not None and current == previous:
            print("复用知识库导入工具：后端输入与 JAR 均未变化。", flush=True)
            return False
        # 失败或构建期间输入变化时不留下可复用标记。
        stamp.unlink(missing_ok=True)
        print("构建知识库导入工具：输入或成品变化，或尚无有效缓存。", flush=True)
        subprocess.run(command, cwd=root, check=True)
        if not jar.is_file():
            raise ValueError(f"构建完成但未生成 JAR：{jar}")
        if inputs_digest(root, command, toolchain) != inputs:
            raise ValueError("构建期间后端输入发生变化，请重新执行")
        temporary = cache / "build.json.tmp"
        temporary.write_text(json.dumps({"inputs": inputs, "jar": digest_file(jar)}))
        temporary.replace(stamp)
        return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("缺少 Maven 构建命令")
    root = args.root.resolve()
    jar = args.jar if args.jar.is_absolute() else root / args.jar
    try:
        java = subprocess.run(["java", "-version"], capture_output=True, check=True, text=True)
        ensure_build(root, jar, command, args.force, java.stdout + java.stderr)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"知识库工具构建失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
