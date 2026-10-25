#!/usr/bin/env python3
"""从 CHANGELOG.md 抓指定版本的段落，写成 release_notes.md。

用法：python3 tools/extract_changelog.py 1.3.2
"""

import os
import re
import sys

VERSION = sys.argv[1].lstrip("v")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
text = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()

pattern = re.compile(r"^## \[" + re.escape(VERSION) + r"\].*?$(.*?)(?=^## |\Z)", re.M | re.S)
match = pattern.search(text)
body = match.group(1).strip() if match else ""

if not body:
    body = f"版本 {VERSION} 的更新日志见 CHANGELOG.md。"

out = os.path.join(os.getcwd(), "release_notes.md")
open(out, "w", encoding="utf-8").write(body)
print(f"release notes for {VERSION}: {len(body)} bytes")
