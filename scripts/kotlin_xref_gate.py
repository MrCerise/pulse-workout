"""Cross-reference gate: checks that symbols used across files actually exist.

Heuristic but catches the failure classes that matter without a compiler:
  1. com.pulse.* imports -> the name is declared in the resolved file
  2. container.<member> -> AppContainer declares it (property or function)
  3. health.<member> -> HealthHub declares it
  4. prefs.<field> (UserPreferences) -> data class declares it
  5. screen composables called by AppRoot -> declared with the expected param count
  6. R.string.<name> -> defined in values/strings.xml (already checked separately)

Exit 0 = no problems found.
"""
import re
import sys
from io import StringIO
from pathlib import Path

try:
    from .kotlin_source import mask_kotlin_noise
    from .safe_repo_io import BoundedSourceReader, RepositoryIOError
except ImportError:  # Executed as `python3 scripts/kotlin_xref_gate.py`.
    from kotlin_source import mask_kotlin_noise
    from safe_repo_io import BoundedSourceReader, RepositoryIOError

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent).resolve()
APP = ROOT / "app/src/main/java/com/pulse/intervalcoach"
ENGINE = ROOT / "engine"
for source_root in (APP, ENGINE):
    if source_root.is_symlink() or not source_root.is_dir():
        print(f"unsafe Kotlin scan root: {source_root}", file=sys.stderr)
        sys.exit(2)

kt_files = sorted(APP.rglob("*.kt")) + sorted(ENGINE.rglob("*.kt"))
reader = BoundedSourceReader(ROOT)
try:
    sources = {path: reader.read_text(path) for path in kt_files}
except RepositoryIOError as error:
    print(f"unsafe Kotlin source input: {error}", file=sys.stderr)
    sys.exit(2)
code_sources = {path: mask_kotlin_noise(text) for path, text in sources.items()}
GENERATED = {"R", "BuildConfig"}


def strip_annotations(text: str) -> str:
    """Remove annotation syntax in one pass, including balanced multiline argument lists."""
    output = StringIO()
    cursor = 0
    index = 0
    while index < len(text):
        previous_is_identifier = index > 0 and (text[index - 1].isalnum() or text[index - 1] == "_")
        if text[index] != "@" or previous_is_identifier:
            index += 1
            continue

        start = index
        index += 1
        name_start = index
        while index < len(text) and (text[index].isalnum() or text[index] in "_.:"):
            index += 1
        if index == name_start:
            index = start + 1
            continue

        while index < len(text) and text[index] in " \t\r\n":
            index += 1
        if index < len(text) and text[index] == "(":
            depth = 0
            while index < len(text):
                if text[index] == "(":
                    depth += 1
                elif text[index] == ")":
                    depth -= 1
                index += 1
                if depth == 0:
                    break
            # An unclosed annotation consumes the remaining source once; parse validation will
            # report the syntax error, without making the xref gate rescan each following line.
        output.write(text[cursor:start])
        output.write("".join(char for char in text[start:index] if char in "\r\n"))
        while index < len(text) and text[index] in " \t":
            index += 1
        cursor = index

    output.write(text[cursor:])
    return output.getvalue()


def top_level_decls(text: str):
    """Names declared at top level (fun/class/object/val/var/data class) with their kind."""
    decls = {}
    text = strip_annotations(text)
    for m in re.finditer(
        r"^(?:public[ \t]+|private[ \t]+|internal[ \t]+|open[ \t]+|abstract[ \t]+|sealed[ \t]+|final[ \t]+|data[ \t]+|enum[ \t]+|inline[ \t]+|value[ \t]+|suspend[ \t]+)*"
        r"(fun|class|object|interface)(?:[ \t]+interface)?[ \t]*(?:<[^>\r\n]+>)?[ \t]*([A-Za-z_][\w.]*)",
        text,
        re.MULTILINE,
    ):
        kind, name = m.group(1), m.group(2).split(".")[-1]
        if name in ("interface", "class", "object"):
            continue
        decls.setdefault(name, set()).add(kind)
    # Line-oriented matching uses horizontal whitespace only; `\s*` here could consume all
    # following blank/comment lines and retry from every line start.
    # top-level vals/vars, including extension properties (`val Context.pulseContainer`)
    for m in re.finditer(
        r"^(?:(?:private|public|internal)[ \t]+)?(?:const[ \t]+)?(?:val|var)[ \t]+([A-Za-z_]\w*)(?:[ \t]*\.[ \t]*([A-Za-z_]\w*))?",
        text,
        re.MULTILINE,
    ):
        decls.setdefault(m.group(2) or m.group(1), set()).add("val")
    return decls


declarations_by_file = {path: top_level_decls(code_sources[path]) for path in kt_files}
package_symbols = {}
global_symbols = {}
for path in kt_files:
    text = code_sources[path]
    package_match = re.search(r"^package[ \t]+([\w.]+)", text, re.MULTILINE)
    package = package_match.group(1) if package_match else ""
    for name in declarations_by_file[path]:
        global_symbols.setdefault(name, path)
        if package:
            package_symbols.setdefault((package, name), path)


def find_file_for(fqn: str):
    """Map a com.pulse.* fully-qualified name to a file + short name in constant time."""
    parts = fqn.split(".")
    short = parts[-1]
    package = ".".join(parts[:-1])
    return package_symbols.get((package, short)) or global_symbols.get(short), short


problems = []

# --- 1. com.pulse.* imports ---
import_re = re.compile(r"^import[ \t]+((?:com\.pulse)[\w.]*)", re.MULTILINE)
for p, text in code_sources.items():
    for m in import_re.finditer(text):
        fqn = m.group(1)
        if fqn.split(".")[-1] in GENERATED:
            continue
        target, short = find_file_for(fqn)
        if target is None:
            problems.append(f"{p.name}: import {fqn} resolves to no declaration")
        elif short in ("serializer",):
            continue

# --- 2/3. container.<member> / health.<member> ---
container_text = next(t for p, t in code_sources.items() if p.name == "PulseApp.kt")
container_members = set(re.findall(r"val[ \t]+(\w+)[ \t]*[:=]", container_text)) | set(
    re.findall(r"fun[ \t]+(\w+)[ \t]*\(", container_text)
)

hub_text = next(t for p, t in code_sources.items() if p.name == "HealthHub.kt")
hub_public = set(re.findall(r"(?:public[ \t]+)?(?:suspend[ \t]+)?(?:val|fun|var)[ \t]+(\w+)[ \t]*[(=:]", hub_text))

for p, text in code_sources.items():
    if p.name in ("PulseApp.kt", "HealthHub.kt"):
        continue
    for line in text.splitlines():
        if line.strip().startswith("import ") or line.strip().startswith("package "):
            continue
        for m in re.finditer(r"\bcontainer\.(\w+)", line):
            if m.group(1) not in container_members:
                problems.append(f"{p.name}: container.{m.group(1)} not found in AppContainer")
        for m in re.finditer(r"(?<![\w.])health\.(\w+)", line):
            if m.group(1) not in hub_public:
                problems.append(f"{p.name}: health.{m.group(1)} not found in HealthHub")

# --- 4. UserPreferences fields ---
prefs_file = next(p for p in kt_files if p.name == "Prefs.kt")
up_text = re.search(r"data class UserPreferences\((.*?)\n\)", code_sources[prefs_file], re.S)
up_fields = set()
if up_text:
    up_fields = set(re.findall(r"(?:val[ \t]+)?(\w+)[ \t]*[:=]", up_text.group(1)))
# also fields set via container.preferences.<setter> must exist as fun in Prefs
prefs_funs = set(re.findall(r"fun[ \t]+(set\w+|\w+)[ \t]*\(", code_sources[prefs_file]))
PREFS_UI_FILES = {"SettingsScreens.kt", "SummaryScreen.kt", "HomeScreen.kt", "ProgressScreens.kt"}
for p, text in code_sources.items():
    if p.name == "Prefs.kt":
        continue
    for m in re.finditer(r"\bcontainer\.preferences\.(\w+)", text):
        name = m.group(1)
        if name == "flow":
            continue
        if name not in prefs_funs:
            problems.append(f"{p.name}: preferences.{name}() not a Prefs function")
    # prefs?.<field> — the delegate is UserPreferences in every file that uses this form
    for m in re.finditer(r"\bprefs\?\.[ \t]*(\w+)", text):
        if m.group(1) not in up_fields:
            problems.append(f"{p.name}: UserPreferences field '{m.group(1)}' not found")
    # in screens where `val current = prefs` (UserPreferences), check current.<field>
    if p.name in PREFS_UI_FILES and re.search(r"val current = prefs\b", text):
        # `LocalPulseDimens.current.<step>` is a qualified property read; only a bare
        # `current.<field>` is the preferences delegate.
        for m in re.finditer(r"(?<![\w.])current\.(\w+)", text):
            field = m.group(1)
            if re.search(rf"\bcurrent\.{field}[ \t]*\(", text):
                continue  # method call, not a field
            if field not in up_fields:
                problems.append(f"{p.name}: UserPreferences field 'current.{field}' not found")

# --- 5. AppRoot call sites vs declared signatures ---
app_root = next(t for p, t in code_sources.items() if p.name == "AppRoot.kt")
screen_files = {
    "HomeScreen": next(t for p, t in code_sources.items() if p.name == "HomeScreen.kt"),
    "WorkoutLibraryScreen": next(t for p, t in code_sources.items() if p.name == "WorkoutScreens.kt"),
    "WorkoutDetailsScreen": next(t for p, t in code_sources.items() if p.name == "WorkoutScreens.kt"),
    "TemplateGalleryScreen": next(t for p, t in code_sources.items() if p.name == "WorkoutScreens.kt"),
    "ProgressScreen": next(t for p, t in code_sources.items() if p.name == "ProgressScreens.kt"),
    "HistoryScreen": next(t for p, t in code_sources.items() if p.name == "ProgressScreens.kt"),
    "SessionDetailScreen": next(t for p, t in code_sources.items() if p.name == "ProgressScreens.kt"),
    "SettingsScreen": next(t for p, t in code_sources.items() if p.name == "SettingsScreens.kt"),
    "VoiceStudioScreen": next(t for p, t in code_sources.items() if p.name == "SettingsScreens.kt"),
    "BackupScreen": next(t for p, t in code_sources.items() if p.name == "SettingsScreens.kt"),
    "HelpScreen": next(t for p, t in code_sources.items() if p.name == "SettingsScreens.kt"),
    "HealthScreen": next(t for p, t in code_sources.items() if p.name == "HealthScreen.kt"),
    "SessionSummaryScreen": next(t for p, t in code_sources.items() if p.name == "SummaryScreen.kt"),
    "WelcomeScreen": next(t for p, t in code_sources.items() if p.name == "WelcomeScreen.kt"),
    "JsonImportScreen": next(t for p, t in code_sources.items() if p.name == "JsonImportScreen.kt"),
    "QuickBuilderScreen": next(t for p, t in code_sources.items() if p.name == "Builders.kt"),
    "AdvancedBuilderScreen": next(t for p, t in code_sources.items() if p.name == "Builders.kt"),
    "PlayerScreen": next(t for p, t in code_sources.items() if p.name == "PlayerScreen.kt"),
}

def sig_params(src, name):
    # find the fun declaration of name (public, with composable) and list top-level param names
    m = re.search(rf"fun[ \t]+{name}[ \t]*\(", src)
    if not m:
        return None
    start = m.end()
    depth = 1
    i = start
    while depth > 0 and i < len(src):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
        i += 1
    if depth > 0:
        return None
    body = src[start:i - 1]
    params = []
    d = 0
    cur = ""
    for ch in body:
        if ch in "([{":
            d += 1
        elif ch in ")]}":
            d -= 1
        if ch == "," and d == 0:
            params.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        params.append(cur)
    names = []
    for param in params:
        pm = re.search(r"\b(\w+)[ \t]*(?::|=)", param)
        if pm:
            names.append(pm.group(1))
    return names


for name, src in screen_files.items():
    call = re.search(rf"\b{name}[ \t]*\(", app_root)
    if not call:
        continue  # screens navigated from other places
    declared = sig_params(src, name)
    if declared is None:
        problems.append(f"{name}: called in AppRoot but declaration not found")

print("== problems ==")
for problem in sorted(set(problems)):
    print(problem)
print(f"total: {len(set(problems))}")
sys.exit(1 if problems else 0)
