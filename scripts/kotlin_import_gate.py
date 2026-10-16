"""Import gate: checks that every shared symbol a file uses is actually imported there.

A Kotlin file can name a symbol that exists elsewhere in the module and still fail to compile:
the name has to be *imported*. A missing `import ...components.PulseDialog` in one screen is an
"unresolved reference" at compile time and a silent, invisible mistake in a text-level review —
the symbol is right there in the codebase, just not in scope.

The gate resolves, for every capitalised identifier a file uses:
  1. declared in that same file                            -> in scope
  2. imported in that file                                -> in scope
  3. declared in a *top-level* declaration of another file of the same package -> in scope
     (a file in the same package does not import its neighbour)
  4. declared at the top level of a file in another package -> MISSING IMPORT
  5. anything else                                        -> left alone (it is the platform's)

Only the fourth case is reported: a name that this module declares, somewhere else, that this file
has not brought into scope. A second pass covers the platform's own extension functions: the
module's Compose imports are the evidence of which names are extensions, and a file that calls
one of them — `.fillMaxHeight(` — without importing it is reported too.

Exit 0 = no problems found.
"""
import re
import sys
from pathlib import Path

try:
    from .kotlin_source import mask_kotlin_noise
    from .safe_repo_io import BoundedSourceReader, RepositoryIOError
except ImportError:  # Executed as `python3 scripts/kotlin_import_gate.py`.
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

MODIFIERS = (
    r"public |private |internal |open |abstract |sealed |data |enum |value |annotation |inline |"
    r"suspend |operator |infix |external |const |lateinit |override |expect |actual |tailrec |"
    r"companion |inner |crossinline |noinline |reified "
)
# A top-level declaration: it starts in column 0, so it is importable by its simple name.
TOP_LEVEL = re.compile(
    r"^(?:" + MODIFIERS + r")*"
    r"(?:fun|class|object|interface|val|var|typealias)\s+(?:<[^>\n]+>\s*)?"
    r"(?:\??[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*\.)?"
    r"([A-Za-z_]\w*)(?!\?)",
    re.M,
)
# Any declaration, at any depth: enough to know the name is already in scope in this file.
ANY_DECLARATION = re.compile(
    r"^\s*(?:" + MODIFIERS + r")*"
    r"(?:fun|class|object|interface|val|var|typealias)\s+(?:<[^>\n]+>\s*)?"
    r"(?:\??[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*\.)?"
    r"([A-Za-z_]\w*)(?!\?)",
    re.M,
)
# An enum entry: a constant inside an enum body.
ENUM_ENTRY = re.compile(r"^\s+([A-Z][A-Z0-9_]*)\s*[,;(\n]", re.M)
# A capitalised name used as a type or a call.
USE = re.compile(r"(?<![\w.\"'`$])([A-Z][A-Za-z0-9_]*)")
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.M)


# Names the platform provides: they are never importable, so they are never "missing".
PLATFORM = {
    "String", "Int", "Long", "Float", "Double", "Boolean", "Byte", "Short", "Char", "Unit",
    "Any", "Nothing", "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Pair",
    "Triple", "Array", "Sequence", "ArrayList", "HashMap", "HashSet", "IntRange", "LongRange",
    "CharRange", "ClosedRange", "IntProgression", "Result", "Regex", "Throwable", "Exception",
    "RuntimeException", "IllegalArgumentException", "IllegalStateException", "Number",
    "CharSequence", "Comparable", "Iterable", "Collection", "Function", "Enum", "Deprecated",
    "Suppress", "JvmStatic", "JvmField", "JvmOverloads", "Throws", "Override", "OptIn",
    "Synchronized", "Volatile", "IntArray", "LongArray", "FloatArray", "DoubleArray", "ByteArray",
    "CharArray", "BooleanArray", "StringBuilder", "Lazy", "Annotation", "Target", "Retention",
    "Source", "Value", "Entity", "Dao", "Database", "PrimaryKey", "ColumnInfo", "Index",
    "TypeConverter", "Transaction", "Query", "Insert", "Update", "Delete", "ForeignKey",
    "Embedded", "Ignore", "ProvidedTypeConverter", "Constructor", "Bundle", "Context", "Intent",
    "Uri", "View", "ViewGroup", "TextView", "ImageView", "Toast", "Notification", "PendingIntent",
    "WorkManager", "CoroutineWorker", "WorkerParameters", "ListenableWorker", "Operation",
    "ComponentName", "Build", "Parcelable", "Serializable", "SparseArray", "Rect", "Point",
    "Color", "StateFlow", "SharedFlow", "Flow", "MutableStateFlow", "MutableSharedFlow", "Job",
    "CoroutineScope", "Dispatchers", "LaunchedEffect", "DerivedStateOf", "SnapshotState",
    "MutableState", "State", "LazyColumn", "LazyRow", "LazyVerticalGrid", "LazyHorizontalGrid",
    "LazyListScope", "LazyGridScope", "LazyItemScope", "Path", "File", "Files", "Paths", "UUID",
    "MessageDigest", "Base64", "Charset", "Charsets", "LocalDate", "LocalTime", "LocalDateTime",
    "Instant", "ZoneId", "ZoneOffset", "DateTimeFormatter", "DateTimeFormatterBuilder", "Duration",
    "Locale", "ChronoUnit", "DayOfWeek", "Month", "Year", "TimeUnit", "ConcurrentHashMap",
    "CopyOnWriteArrayList", "Collections", "Executors", "CountDownLatch", "Timer", "TimerTask",
    "AutoMigration", "AutoMigrationSpec", "Migration", "Room",
}

# Every Compose name the module imports anywhere is an API the module knows about. A file that
# *uses* one of them — as a bare call `rememberCoroutineScope()` or as an extension call
# `.fillMaxHeight(` — without importing it is missing an import: an extension function is not a
# member of its receiver, so the import is what brings it into scope.
COMPOSE_CALL = re.compile(r"(?<![\w.])(\w+)\s*\(")
COMPOSE_EXTENSION_CALL = re.compile(r"\.([a-z][A-Za-z0-9_]*)\s*\(")

universe = set()
for path, source in code.items():
    for line in source.split("\n"):
        if line.startswith("import ") and "androidx.compose" in line:
            universe.add(line.split(" ")[1].split(" as ")[-1].strip().split(".")[-1])

# The packages whose names are top-level functions: a bare call to one of them needs an import.
# The lazy-list scope's own members (`items`, `item`) do not, so that package is left out.
BARE_CALL_PACKAGES = (
    "androidx.compose.runtime",
    "androidx.compose.ui",
    "androidx.compose.foundation.layout",
    "androidx.compose.material3",
)
bare_universe = set()
for path, source in code.items():
    for line in source.split("\n"):
        if line.startswith("import ") and any(
            line.split(" ")[1].strip().startswith(prefix) for prefix in BARE_CALL_PACKAGES
        ):
            bare_universe.add(line.split(" ")[1].split(" as ")[-1].strip().split(".")[-1])

problems = []
for path, source in code.items():
    package = PACKAGE.search(source)
    package = package.group(1) if package else ""
    lines = source.split("\n")
    imported = set()
    for line in lines:
        if line.startswith("import "):
            fqn = line.split(" ")[1].split(" as ")[-1].strip()
            imported.add(fqn.split(".")[-1])
    body = "\n".join(
        line for line in lines
        if not line.startswith("import ") and not line.startswith("package ")
    )
    # The names this file declares itself, at any depth, plus its enum entries.
    own = {m.group(1) for m in ANY_DECLARATION.finditer(body)}
    own |= {m.group(1) for m in ENUM_ENTRY.finditer(body)}
    # The top-level names of every other file, with the package they live in.
    for name in sorted(set(USE.findall(body))):
        if name in imported or name in own or name in PLATFORM:
            continue
        for other, other_source in code.items():
            if other == path:
                continue
            match = TOP_LEVEL.search(other_source)
            names = {m.group(1) for m in TOP_LEVEL.finditer(other_source)}
            if name not in names:
                continue
            other_package = PACKAGE.search(other_source)
            other_package = other_package.group(1) if other_package else ""
            if other_package == package:
                break  # same package: no import needed
            problems.append(
                f"{path.relative_to(ROOT)}: '{name}' is used but not imported "
                f"(declared in {other.relative_to(ROOT)})"
            )
            break
    # The extension-call pass: `.name(` where `name` is an API the module imports elsewhere.
    imported_here = set()
    for line in source.split("\n"):
        if line.startswith("import "):
            imported_here.add(line.split(" ")[1].split(" as ")[-1].strip().split(".")[-1])
    used = set(COMPOSE_CALL.findall(body)) | set(COMPOSE_EXTENSION_CALL.findall(body))
    for name in sorted(used):
        if name in imported_here or name in own or name in PLATFORM:
            continue
        if name not in universe:
            continue
        # A fully-qualified call — `androidx.compose.runtime.rememberCoroutineScope()` — needs no
        # import: the package is in the name.
        if re.search(r"[A-Za-z0-9_]\." + re.escape(name) + r"\s*\(", body):
            continue
        # The lazy-list scope's own members need no import.
        if name in ("item", "items", "stickyHeader"):
            continue
        # A name the module also declares at the top level in this file's own package is a local
        # type: same package, no import needed.
        if any(
            name in {m.group(1) for m in TOP_LEVEL.finditer(other_source)}
            and (PACKAGE.search(other_source).group(1) if PACKAGE.search(other_source) else "")
            == package
            for other_source in code.values()
        ):
            continue
        problems.append(
            f"{path.relative_to(ROOT)}: '{name}' is used but not imported"
        )

print("== problems ==")
for problem in sorted(set(problems)):
    print(problem)
print(f"total: {len(set(problems))}")
sys.exit(1 if problems else 0)
