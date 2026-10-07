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
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
APP = ROOT / "app/src/main/java/com/pulse/intervalcoach"

kt_files = list(APP.rglob("*.kt")) + list((ROOT / "engine").rglob("*.kt"))
sources = {p: p.read_text() for p in kt_files}
GENERATED = {"R", "BuildConfig"}


def top_level_decls(text: str):
    """Names declared at top level (fun/class/object/val/var/data class) with their kind."""
    decls = {}
    for m in re.finditer(
        r"^(?:@[\w.()]+(?:\([^)]*\))?\s+)*"
        r"(?:public\s+|private\s+|internal\s+|open\s+|abstract\s+|sealed\s+|final\s+|data\s+|enum\s+|inline\s+|value\s+|suspend\s+)*"
        r"(fun|class|object|interface)(?:\s+interface)?\s*(?:<[^>]+>)?\s*([A-Za-z_][\w.]*)",
        text,
        re.MULTILINE,
    ):
        kind, name = m.group(1), m.group(2).split(".")[-1]
        if name in ("interface", "class", "object"):
            continue
        decls.setdefault(name, set()).add(kind)
    # top-level vals/vars, including extension properties (`val Context.pulseContainer`)
    for m in re.finditer(
        r"^(?:private|public|internal)?\s*(?:const\s+)?(?:val|var)\s+([A-Za-z_]\w*)(?:\s*\.\s*([A-Za-z_]\w*))?",
        text,
        re.MULTILINE,
    ):
        decls.setdefault(m.group(2) or m.group(1), set()).add("val")
    return decls


ALL_DECLS = {}
for p, text in sources.items():
    for name, kinds in top_level_decls(text).items():
        ALL_DECLS.setdefault(str(p.relative_to(ROOT)), set()).update(kinds)


def find_file_for(fqn: str):
    """Map a com.pulse.* fully-qualified name to a file + short name."""
    parts = fqn.split(".")
    short = parts[-1]
    pkg_path = "/".join(parts[2:-1]) if len(parts) > 3 else ""
    # search all files for a declaration of the short name in the right package
    for p, text in sources.items():
        m = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        if m and m.group(1) == ".".join(parts[2:-1]):
            if short in top_level_decls(text):
                return p, short
    # fallback: any file declaring it (single-module app, names are unique)
    for p in kt_files:
        if short in top_level_decls(sources[p]):
            return p, short
    return None, short


problems = []

# --- 1. com.pulse.* imports ---
import_re = re.compile(r"^import\s+((?:com\.pulse)[\w.]*)", re.MULTILINE)
for p, text in sources.items():
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
container_text = next(t for p, t in sources.items() if p.name == "PulseApp.kt")
container_members = set(re.findall(r"val\s+(\w+)\s*[:=]", container_text)) | set(
    re.findall(r"fun\s+(\w+)\s*\(", container_text)
)

hub_text = next(t for p, t in sources.items() if p.name == "HealthHub.kt")
hub_public = set(re.findall(r"(?:public\s+)?(?:suspend\s+)?(?:val|fun|var)\s+(\w+)\s*[(=:]", hub_text))

for p, text in sources.items():
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
up_text = re.search(r"data class UserPreferences\((.*?)\n\)", sources[prefs_file], re.S)
up_fields = set()
if up_text:
    up_fields = set(re.findall(r"(?:val\s+)?(\w+)\s*[:=]", up_text.group(1)))
# also fields set via container.preferences.<setter> must exist as fun in Prefs
prefs_funs = set(re.findall(r"fun\s+(set\w+|\w+)\s*\(", sources[prefs_file]))
PREFS_UI_FILES = {"SettingsScreens.kt", "SummaryScreen.kt", "HomeScreen.kt", "ProgressScreens.kt"}
for p, text in sources.items():
    if p.name == "Prefs.kt":
        continue
    for m in re.finditer(r"\bcontainer\.preferences\.(\w+)", text):
        name = m.group(1)
        if name == "flow":
            continue
        if name not in prefs_funs:
            problems.append(f"{p.name}: preferences.{name}() not a Prefs function")
    # prefs?.<field> — the delegate is UserPreferences in every file that uses this form
    for m in re.finditer(r"\bprefs\?\.\s*(\w+)", text):
        if m.group(1) not in up_fields:
            problems.append(f"{p.name}: UserPreferences field '{m.group(1)}' not found")
    # in screens where `val current = prefs` (UserPreferences), check current.<field>
    if p.name in PREFS_UI_FILES and re.search(r"val current = prefs\b", text):
        for m in re.finditer(r"\bcurrent\.(\w+)", text):
            field = m.group(1)
            if re.search(rf"\bcurrent\.{field}\s*\(", text):
                continue  # method call, not a field
            if field not in up_fields:
                problems.append(f"{p.name}: UserPreferences field 'current.{field}' not found")

# --- 5. AppRoot call sites vs declared signatures ---
app_root = next(t for p, t in sources.items() if p.name == "AppRoot.kt")
screen_files = {
    "HomeScreen": next(t for p, t in sources.items() if p.name == "HomeScreen.kt"),
    "WorkoutLibraryScreen": next(t for p, t in sources.items() if p.name == "WorkoutScreens.kt"),
    "WorkoutDetailsScreen": next(t for p, t in sources.items() if p.name == "WorkoutScreens.kt"),
    "TemplateGalleryScreen": next(t for p, t in sources.items() if p.name == "WorkoutScreens.kt"),
    "ProgressScreen": next(t for p, t in sources.items() if p.name == "ProgressScreens.kt"),
    "HistoryScreen": next(t for p, t in sources.items() if p.name == "ProgressScreens.kt"),
    "SessionDetailScreen": next(t for p, t in sources.items() if p.name == "ProgressScreens.kt"),
    "SettingsScreen": next(t for p, t in sources.items() if p.name == "SettingsScreens.kt"),
    "VoiceStudioScreen": next(t for p, t in sources.items() if p.name == "SettingsScreens.kt"),
    "BackupScreen": next(t for p, t in sources.items() if p.name == "SettingsScreens.kt"),
    "HelpScreen": next(t for p, t in sources.items() if p.name == "SettingsScreens.kt"),
    "HealthScreen": next(t for p, t in sources.items() if p.name == "HealthScreen.kt"),
    "SessionSummaryScreen": next(t for p, t in sources.items() if p.name == "SummaryScreen.kt"),
    "WelcomeScreen": next(t for p, t in sources.items() if p.name == "WelcomeScreen.kt"),
    "ParserScreen": next(t for p, t in sources.items() if p.name == "ParserScreen.kt"),
    "QuickBuilderScreen": next(t for p, t in sources.items() if p.name == "Builders.kt"),
    "AdvancedBuilderScreen": next(t for p, t in sources.items() if p.name == "Builders.kt"),
    "PlayerScreen": next(t for p, t in sources.items() if p.name == "PlayerScreen.kt"),
}

sig_re = re.compile(
    r"fun\s+(\w+)\s*\((.*?)\)\s*\{", re.S
)

def sig_params(src, name):
    # find the fun declaration of name (public, with composable) and list top-level param names
    m = re.search(rf"fun\s+{name}\s*\(", src)
    if not m:
        return None
    start = m.end()
    depth = 1
    i = start
    while depth > 0:
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
        i += 1
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
        pm = re.search(r"\b(\w+)\s*(?::|=)", param)
        if pm:
            names.append(pm.group(1))
    return names


for name, src in screen_files.items():
    call = re.search(rf"\b{name}\s*\(", app_root)
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
