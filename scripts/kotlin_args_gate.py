"""Named-argument gate: checks that every named argument at a call site exists on the declaration.

The Kotlin parser gate proves the files are syntactically valid; this one proves the *names* line
up. A renamed parameter — `PulseIconTile(tint = …)` after the parameter became `contentColor`, or
`StatTile(value = …)` after it became `caption` — is a compile error that no amount of parsing
catches, and it is exactly the kind of mistake a component rework makes when a shared component's
signature changes and every call site has to be revisited.

Heuristic, like the other Kotlin gates: it reads the declaration's parameter list and the call
site's argument list with the same splitter, so a call that passes an argument positionally is
never mistaken for a named one.

Exit 0 = no problems found.
"""
import re
import sys
from pathlib import Path

try:
    from .kotlin_source import mask_kotlin_noise
    from .safe_repo_io import BoundedSourceReader, RepositoryIOError
except ImportError:  # Executed as `python3 scripts/kotlin_args_gate.py`.
    from kotlin_source import mask_kotlin_noise
    from safe_repo_io import BoundedSourceReader, RepositoryIOError

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent).resolve()
APP = ROOT / "app/src/main/java/com/pulse/intervalcoach"
OTHER_MODULES = [
    ROOT / "engine/src/main/kotlin",
    ROOT / "sync/src/main/kotlin",
    ROOT / "library/src/main/kotlin",
    ROOT / "wear/src/main/java",
]
for source_root in [APP] + OTHER_MODULES:
    if source_root.is_symlink() or not source_root.is_dir():
        print(f"unsafe Kotlin scan root: {source_root}", file=sys.stderr)
        sys.exit(2)

kt_files = sorted(APP.rglob("*.kt")) + [f for root in OTHER_MODULES for f in sorted(root.rglob("*.kt"))]
reader = BoundedSourceReader(ROOT)
try:
    sources = {path: reader.read_text(path) for path in kt_files}
except RepositoryIOError as error:
    print(f"unsafe Kotlin source input: {error}", file=sys.stderr)
    sys.exit(2)
code = {path: mask_kotlin_noise(text) for path, text in sources.items()}

FUN = re.compile(
    r"^(?:@\w+(?:\([^\n]*\))?\s+)*"
    r"(?:public |private |internal |open |abstract |sealed |inline |suspend |operator |infix |external )*"
    r"fun\s+([A-Za-z_]\w*)\s*(?:<[^>\n]*>)?\s*\(",
    re.M,
)
CALL = re.compile(r"(?<![\w.])([A-Z][A-Za-z0-9_]*)\s*\(")
IDENTIFIER = re.compile(r"[a-z_]\w*")


def split_top_level(text):
    """Split an argument list on the commas that separate arguments, not the ones inside one."""
    depth = 0
    parts = []
    current = ""
    for char in text:
        if char in "({":
            depth += 1
        elif char in ")}":
            depth -= 1
        if char == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    if current.strip():
        parts.append(current)
    return parts


def matching_paren(text, open_index):
    depth = 0
    index = open_index
    while index < len(text):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index
        index += 1
    return -1


def named_arguments(argument_list):
    """The named arguments in one call: the ones written `name = value` at the top level."""
    names = []
    for argument in split_top_level(argument_list):
        depth = 0
        equals = -1
        for index, char in enumerate(argument):
            if char in "({":
                depth += 1
            elif char in ")}":
                depth -= 1
            elif char == "=" and depth == 0:
                before = argument[index - 1] if index else ""
                after = argument[index + 1] if index + 1 < len(argument) else ""
                if before in "=!<>" or after == "=":
                    continue  # ==, <=, >=, !=
                equals = index
                break
        if equals <= 0:
            continue
        head = argument[:equals].strip()
        if IDENTIFIER.fullmatch(head):
            names.append(head)
    return names


def declared_parameters(parameter_list):
    names = set()
    for parameter in split_top_level(parameter_list):
        head = parameter.split("=")[0].strip()
        head = head.split(":")[0].strip()
        head = head.split()[0] if head.split() else ""
        if IDENTIFIER.fullmatch(head):
            names.add(head)
    return names


# --- Declarations ---------------------------------------------------------------------------------
declarations = {}
for path, source in code.items():
    for match in FUN.finditer(source):
        close = matching_paren(source, match.end() - 1)
        if close < 0:
            continue
        parameters = declared_parameters(source[match.end():close])
        declarations.setdefault(match.group(1), set()).update(parameters)

# --- Call sites -----------------------------------------------------------------------------------
problems = []
checked = 0
for path, source in code.items():
    for match in CALL.finditer(source):
        name = match.group(1)
        if name not in declarations:
            continue
        close = matching_paren(source, match.end() - 1)
        if close < 0:
            continue
        arguments = source[match.end():close]
        if not arguments.strip():
            continue
        for argument in named_arguments(arguments):
            checked += 1
            if argument not in declarations[name]:
                problems.append(
                    f"{path.relative_to(ROOT)}: {name}(… {argument} = …) — "
                    f"no parameter '{argument}' on {name}()"
                )

print("== problems ==")
for problem in sorted(set(problems)):
    print(problem)
print(f"named arguments checked: {checked}")
print(f"total: {len(set(problems))}")
sys.exit(1 if problems else 0)
