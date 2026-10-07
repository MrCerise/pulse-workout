"""Parse gate: tree-sitter syntax check for every Kotlin file in the app module.

Exit 0 = all files parse clean. Any ERROR/MISSING node is reported with file:line.
"""
import sys
from pathlib import Path

import tree_sitter_kotlin as tsk
from tree_sitter import Language, Parser

KOTLIN_LANGUAGE = Language(tsk.language())
parser = Parser(KOTLIN_LANGUAGE)

root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
targets = [p for p in root.rglob("*.kt") if "build" not in p.parts]

bad = 0
for path in sorted(targets):
    source = path.read_bytes()
    tree = parser.parse(source)
    problems = []

    def walk(node):
        if node.type == "ERROR" or node.is_missing:
            snippet = source[node.start_byte:node.end_byte].decode("utf-8", "replace")
            problems.append((node.start_point[0] + 1, node.type, snippet[:80]))
        for child in node.children:
            walk(child)

    walk(tree.root_node)
    if problems:
        bad += 1
        print(f"FAIL {path}")
        for line, kind, snippet in problems[:12]:
            print(f"  line {line} [{kind}] {snippet!r}")

print(f"parsed {len(targets)} files, {bad} with syntax errors")
sys.exit(1 if bad else 0)
