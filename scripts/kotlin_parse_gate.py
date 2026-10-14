"""Parse gate: tree-sitter syntax check for every Kotlin file in the app module.

Exit 0 = all files parse clean. Any ERROR/MISSING node is reported with file:line.
"""
import sys
from pathlib import Path

try:
    from .safe_repo_io import BoundedSourceReader, RepositoryIOError
except ImportError:  # Executed as `python3 scripts/kotlin_parse_gate.py`.
    from safe_repo_io import BoundedSourceReader, RepositoryIOError

import tree_sitter_kotlin as tsk
from tree_sitter import Language, Parser

KOTLIN_LANGUAGE = Language(tsk.language())
parser = Parser(KOTLIN_LANGUAGE)

root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent).resolve()
targets = [p for p in root.rglob("*.kt") if "build" not in p.parts]

bad = 0
reader = BoundedSourceReader(root)
for path in sorted(targets):
    try:
        source = reader.read_bytes(path)
    except RepositoryIOError as error:
        bad += 1
        print(f"FAIL {path}\n  unsafe source input: {error}")
        continue
    tree = parser.parse(source)
    problems = []

    pending = [tree.root_node]
    while pending:
        node = pending.pop()
        if node.type == "ERROR" or node.is_missing:
            snippet = source[node.start_byte:node.end_byte].decode("utf-8", "replace")
            problems.append((node.start_point[0] + 1, node.type, snippet[:80]))
        pending.extend(reversed(node.children))
    if problems:
        bad += 1
        print(f"FAIL {path}")
        for line, kind, snippet in problems[:12]:
            print(f"  line {line} [{kind}] {snippet!r}")

print(f"parsed {len(targets)} files, {bad} with syntax errors")
sys.exit(1 if bad else 0)
